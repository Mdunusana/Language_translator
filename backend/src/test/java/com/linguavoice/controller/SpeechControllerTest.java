package com.linguavoice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.translation.TranslationProvider;
import com.linguavoice.integration.tts.TextToSpeechProvider;
import com.linguavoice.integration.tts.Voice;
import java.util.HashMap;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Text-to-speech API with the provider mocked: no paid synthesis is ever performed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpeechControllerTest {

    private static final Voice THANDO = new Voice("zu-ZA-ThandoNeural", "Thando", "zu-ZA", "Female");
    private static final Voice THEMBA = new Voice("zu-ZA-ThembaNeural", "Themba", "zu-ZA", "Male");
    private static final byte[] MP3 = {(byte) 0xFF, (byte) 0xFB, 0x10, 0x00};

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private TextToSpeechProvider ttsProvider;
    @MockitoBean private TranslationProvider translationProvider;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        when(ttsProvider.isConfigured()).thenReturn(true);
        when(ttsProvider.displayName()).thenReturn("Mock Voices");
        when(ttsProvider.maxInputBytes()).thenReturn(Integer.MAX_VALUE);
        when(ttsProvider.voicesByLanguage()).thenReturn(Map.of("zu", List.of(THANDO, THEMBA)));
        when(translationProvider.isConfigured()).thenReturn(true);
        when(translationProvider.displayName()).thenReturn("Mock Translator");
        when(translationProvider.supportedLanguages()).thenReturn(Set.of("en", "zu", "xh"));
        token = registerAndGetToken();
    }

    @Test
    @DisplayName("returns MP3 audio with no-store caching and the default voice when none is chosen")
    void synthesizesWithDefaultVoice() throws Exception {
        when(ttsProvider.synthesize("Sawubona", THANDO, 1.0)).thenReturn(MP3);

        synthesize("Sawubona", "zu", null, null)
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(SpeechController.VOICE_HEADER, "zu-ZA-ThandoNeural"))
                .andExpect(content().bytes(MP3));
    }

    @Test
    @DisplayName("uses the chosen voice and speed")
    void synthesizesWithChosenVoiceAndSpeed() throws Exception {
        when(ttsProvider.synthesize("Sawubona", THEMBA, 1.5)).thenReturn(MP3);

        synthesize("Sawubona", "zu", "zu-ZA-ThembaNeural", 1.5)
                .andExpect(status().isOk())
                .andExpect(header().string(SpeechController.VOICE_HEADER, "zu-ZA-ThembaNeural"));
    }

    @Test
    @DisplayName("rejects voices not offered for the language, languages without voices, and bad speeds")
    void validates() throws Exception {
        synthesize("Hi", "zu", "en-US-JennyNeural", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.voice").value("This voice isn't available for isiZulu."));
        synthesize("Molo", "xh", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.language").value("No voice is available for isiXhosa."));
        synthesize("Hi", "zu", null, 3.0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.speed").exists());
        synthesize("Hi", "zu", "bad'voice", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.voice").exists());
        synthesize("  ", "zu", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.text").exists());
        verify(ttsProvider, never()).synthesize(anyString(), any(), anyDouble());
    }

    @Test
    @DisplayName("enforces the provider's input size limit")
    void enforcesProviderLimit() throws Exception {
        when(ttsProvider.maxInputBytes()).thenReturn(10);
        synthesize("This is longer than ten bytes", "zu", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.text").exists());
    }

    @Test
    @DisplayName("503 when not configured, 502 when the provider fails")
    void providerStates() throws Exception {
        when(ttsProvider.synthesize(anyString(), eq(THANDO), anyDouble()))
                .thenThrow(AiProviderException.failed(TextToSpeechProvider.FEATURE));
        synthesize("Hi", "zu", null, null).andExpect(status().isBadGateway());

        when(ttsProvider.isConfigured()).thenReturn(false);
        synthesize("Hi", "zu", null, null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Text-to-speech is not available: no AI provider is configured on the server."));
    }

    @Test
    @DisplayName("GET /api/speech/voices lists voices, or available=false so the browser can fall back")
    void listsVoices() throws Exception {
        mockMvc.perform(get("/api/speech/voices").param("language", "zu").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.provider").value("Mock Voices"))
                .andExpect(jsonPath("$.voices[0].id").value("zu-ZA-ThandoNeural"))
                .andExpect(jsonPath("$.voices[1].gender").value("Male"));
        mockMvc.perform(get("/api/speech/voices").param("language", "xh").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.voices").isEmpty());
        mockMvc.perform(get("/api/speech/voices").param("language", "klingon").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/languages reports voiceOutput, and survives a voice-list outage")
    void languagesReportVoiceOutput() throws Exception {
        mockMvc.perform(get("/api/languages").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voiceOutputAvailable").value(true))
                .andExpect(jsonPath("$.voiceOutputProvider").value("Mock Voices"))
                .andExpect(jsonPath("$.languages[?(@.code=='zu')].voiceOutput").value(true))
                .andExpect(jsonPath("$.languages[?(@.code=='xh')].voiceOutput").value(false));

        when(ttsProvider.voicesByLanguage()).thenThrow(AiProviderException.failed(TextToSpeechProvider.FEATURE));
        mockMvc.perform(get("/api/languages").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translationAvailable").value(true))
                .andExpect(jsonPath("$.voiceOutputAvailable").value(false));
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/speech/synthesize").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hi\",\"language\":\"zu\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions synthesize(String text, String language, String voice, Double speed) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("language", language);
        body.put("voice", voice);
        body.put("speed", speed);
        return mockMvc.perform(post("/api/speech/synthesize")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .accept("audio/mpeg", "application/json")
                .content(objectMapper.writeValueAsString(body)));
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String registerAndGetToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Speech Tester", "email", "tts-" + UUID.randomUUID() + "@example.com",
                                "password", "Secret123", "confirmPassword", "Secret123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
