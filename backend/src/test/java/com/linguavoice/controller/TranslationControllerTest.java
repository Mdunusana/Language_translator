package com.linguavoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import com.linguavoice.integration.translation.TranslationProvider;
import com.linguavoice.integration.translation.TranslationResult;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full HTTP → service → database flow with the translation provider replaced by a Mockito mock,
 * so no paid API is called. Each test registers its own user (and therefore its own rate-limit bucket).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.rate-limit.ai-requests-per-minute=3")
class TranslationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferenceRepository preferenceRepository;
    @Autowired private TranslationRepository translationRepository;

    @MockitoBean private TranslationProvider provider;

    @BeforeEach
    void setUp() {
        translationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();
        when(provider.isConfigured()).thenReturn(true);
        when(provider.displayName()).thenReturn("Mock Translator");
        when(provider.supportedLanguages()).thenReturn(Set.of("en", "af", "zu", "fr", "ko"));
    }

    @Test
    @DisplayName("POST /api/translations/text returns 201 and saves the translation for the caller")
    @Transactional
    void translatesAndSaves() throws Exception {
        String token = registerAndGetToken();
        when(provider.translate("Good morning", "en", "af")).thenReturn(new TranslationResult("Goeie môre", null));

        translate(token, "Good morning", "en", "af")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.originalText").value("Good morning"))
                .andExpect(jsonPath("$.translatedText").value("Goeie môre"))
                .andExpect(jsonPath("$.sourceLanguage").value("en"))
                .andExpect(jsonPath("$.sourceDetected").value(false))
                .andExpect(jsonPath("$.targetLanguage").value("af"))
                .andExpect(jsonPath("$.translationType").value("TEXT"));

        List<Translation> saved = translationRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getUser().getEmail()).startsWith("user-");
        assertThat(saved.get(0).getTranslationType()).isEqualTo(TranslationType.TEXT);
    }

    @Test
    @DisplayName("auto-detect reports and stores the detected language")
    void autoDetect() throws Exception {
        String token = registerAndGetToken();
        when(provider.translate(eq("Bonjour"), eq(null), eq("en"))).thenReturn(new TranslationResult("Hello", "fr"));

        translate(token, "Bonjour", "auto", "en")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceLanguage").value("fr"))
                .andExpect(jsonPath("$.sourceDetected").value(true));
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/translations/text").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hi\",\"targetLanguage\":\"af\"}"))
                .andExpect(status().isUnauthorized());
        verify(provider, never()).translate(any(), any(), any());
    }

    @Test
    @DisplayName("validates text and languages on the server")
    void validatesInput() throws Exception {
        String token = registerAndGetToken();

        translate(token, "   ", "en", "af")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.text").exists());
        translate(token, "x".repeat(5001), "en", "af")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.text").value("Text can be at most 5000 characters."));
        translate(token, "Hi", "en", "<script>")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.targetLanguage").exists());
        translate(token, "Hi", "en", "ko") // provider supports ko, but LinguaVoice does not offer it
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.targetLanguage").value("This target language is not supported."));
        translate(token, "Hi", "en", "en")
                .andExpect(status().isBadRequest());

        verify(provider, never()).translate(anyString(), any(), anyString());
        assertThat(translationRepository.count()).isZero();
    }

    @Test
    @DisplayName("returns 429 with Retry-After once the per-user budget is used up")
    void rateLimited() throws Exception {
        String token = registerAndGetToken();
        when(provider.translate(anyString(), anyString(), anyString())).thenReturn(new TranslationResult("x", null));

        for (int i = 0; i < 3; i++) {
            translate(token, "Hi " + i, "en", "af").andExpect(status().isCreated());
        }
        translate(token, "One too many", "en", "af")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        assertThat(translationRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("returns 503 with a clear message when no provider is configured")
    void notConfigured() throws Exception {
        String token = registerAndGetToken();
        when(provider.isConfigured()).thenReturn(false);

        translate(token, "Hi", "en", "af")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Text translation is not available: no AI provider is configured on the server."));
    }

    @Test
    @DisplayName("GET /api/languages lists only catalog languages the provider supports, in catalog order")
    void listsLanguages() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/languages").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translationAvailable").value(true))
                .andExpect(jsonPath("$.provider").value("Mock Translator"))
                .andExpect(jsonPath("$.languages[*].code", contains("en", "af", "zu", "fr")))
                .andExpect(jsonPath("$.languages[2].name").value("isiZulu"))
                .andExpect(jsonPath("$.languages[0].voiceInput").value(false));
    }

    @Test
    @DisplayName("GET /api/languages explains when translation is not configured")
    void languagesWhenNotConfigured() throws Exception {
        String token = registerAndGetToken();
        when(provider.isConfigured()).thenReturn(false);

        mockMvc.perform(get("/api/languages").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translationAvailable").value(false))
                .andExpect(jsonPath("$.languages").isEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private ResultActions translate(String token, String text, String source, String target) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("text", text);
        body.put("sourceLanguage", source);
        body.put("targetLanguage", target);
        return mockMvc.perform(post("/api/translations/text")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String registerAndGetToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Test User", "email", email,
                                "password", "Secret123", "confirmPassword", "Secret123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
