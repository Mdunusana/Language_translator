package com.linguavoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.speech.SpeechToTextProvider;
import com.linguavoice.integration.speech.TestWav;
import com.linguavoice.integration.speech.WavAudio;
import com.linguavoice.integration.translation.TranslationProvider;
import com.linguavoice.integration.translation.TranslationResult;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Voice upload → speech recognition → translation → history, with both AI providers mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoiceTranslationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferenceRepository preferenceRepository;
    @Autowired private TranslationRepository translationRepository;

    @MockitoBean private TranslationProvider translationProvider;
    @MockitoBean private SpeechToTextProvider speechProvider;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        translationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();
        when(translationProvider.isConfigured()).thenReturn(true);
        when(translationProvider.displayName()).thenReturn("Mock Translator");
        when(translationProvider.supportedLanguages()).thenReturn(Set.of("en", "af", "zu", "xh"));
        when(speechProvider.isConfigured()).thenReturn(true);
        when(speechProvider.displayName()).thenReturn("Mock Speech");
        when(speechProvider.supportedLanguages()).thenReturn(Set.of("en", "af", "zu")); // no isiXhosa, like Azure
        token = registerAndGetToken();
    }

    @Test
    @DisplayName("recognises speech, translates it and saves a VOICE record without the audio")
    void translatesVoice() throws Exception {
        when(speechProvider.transcribe(any(WavAudio.class), eq("en"))).thenReturn("Good morning");
        when(translationProvider.translate("Good morning", "en", "zu")).thenReturn(new TranslationResult("Sawubona", null));

        upload(TestWav.valid(2), "en", "zu")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalText").value("Good morning"))
                .andExpect(jsonPath("$.translatedText").value("Sawubona"))
                .andExpect(jsonPath("$.sourceLanguage").value("en"))
                .andExpect(jsonPath("$.translationType").value("VOICE"));

        List<Translation> saved = translationRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTranslationType()).isEqualTo(TranslationType.VOICE);
        assertThat(saved.get(0).getOriginalText()).isEqualTo("Good morning");
    }

    @Test
    @DisplayName("refuses voice input for a language the speech provider doesn't support")
    void unsupportedVoiceLanguage() throws Exception {
        upload(TestWav.valid(1), "xh", "en")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sourceLanguage").value("Voice input isn't available for isiXhosa."));
        verify(speechProvider, never()).transcribe(any(), any());
    }

    @Test
    @DisplayName("rejects uploads that are not 16 kHz mono WAV, before any provider call")
    void rejectsBadAudio() throws Exception {
        upload("not audio at all, just some text pretending to be a file....".getBytes(), "en", "zu")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.audio").exists());
        upload(TestWav.build(1, 44_100, 2, 16), "en", "zu")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.audio").exists());
        upload(TestWav.valid(61), "en", "zu")
                .andExpect(status().isBadRequest());
        verify(speechProvider, never()).transcribe(any(), any());
        assertThat(translationRepository.count()).isZero();
    }

    @Test
    @DisplayName("returns 422 with a helpful message when no speech was recognised, and saves nothing")
    void noSpeech() throws Exception {
        when(speechProvider.transcribe(any(WavAudio.class), eq("en"))).thenReturn("");

        upload(TestWav.valid(1), "en", "zu")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("couldn't make out any speech")));
        verify(translationProvider, never()).translate(any(), any(), any());
        assertThat(translationRepository.count()).isZero();
    }

    @Test
    @DisplayName("returns 503 when no speech provider is configured")
    void notConfigured() throws Exception {
        when(speechProvider.isConfigured()).thenReturn(false);

        upload(TestWav.valid(1), "en", "zu")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Voice input is not available: no AI provider is configured on the server."));
    }

    @Test
    @DisplayName("a speech provider failure is reported and nothing is saved")
    void providerFailure() throws Exception {
        when(speechProvider.transcribe(any(WavAudio.class), eq("en")))
                .thenThrow(AiProviderException.failed(SpeechToTextProvider.FEATURE));

        upload(TestWav.valid(1), "en", "zu").andExpect(status().isBadGateway());
        assertThat(translationRepository.count()).isZero();
    }

    @Test
    @DisplayName("missing parts and same source/target are rejected with 400")
    void validatesRequest() throws Exception {
        mockMvc.perform(multipart("/api/translations/voice")
                        .param("sourceLanguage", "en").param("targetLanguage", "zu")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
        upload(TestWav.valid(1), "en", "en")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.targetLanguage").exists());
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/translations/voice")
                        .file(new MockMultipartFile("audio", "a.wav", "audio/wav", TestWav.valid(1)))
                        .param("sourceLanguage", "en").param("targetLanguage", "zu"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/languages reports voice input per language")
    void languagesReportVoiceInput() throws Exception {
        mockMvc.perform(get("/api/languages").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voiceInputAvailable").value(true))
                .andExpect(jsonPath("$.voiceInputProvider").value("Mock Speech"))
                .andExpect(jsonPath("$.languages[?(@.code=='zu')].voiceInput").value(true))
                .andExpect(jsonPath("$.languages[?(@.code=='xh')].voiceInput").value(false));
    }

    private ResultActions upload(byte[] audio, String source, String target) throws Exception {
        return mockMvc.perform(multipart("/api/translations/voice")
                .file(new MockMultipartFile("audio", "recording.wav", "audio/wav", audio))
                .param("sourceLanguage", source)
                .param("targetLanguage", target)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String registerAndGetToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Voice Tester", "email", "voice-" + UUID.randomUUID() + "@example.com",
                                "password", "Secret123", "confirmPassword", "Secret123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
