package com.linguavoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import com.linguavoice.entity.User;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** History: listing, search, filters, paging, delete and clear, and strict per-user isolation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HistoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferenceRepository preferenceRepository;
    @Autowired private TranslationRepository translationRepository;

    private String adaToken;
    private String graceToken;
    private User ada;
    private User grace;

    @BeforeEach
    void setUp() throws Exception {
        translationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();
        adaToken = register("ada");
        graceToken = register("grace");
        ada = userRepository.findAll().stream().filter(u -> u.getEmail().startsWith("ada")).findFirst().orElseThrow();
        grace = userRepository.findAll().stream().filter(u -> u.getEmail().startsWith("grace")).findFirst().orElseThrow();

        save(ada, "Good morning", "Goeie môre", "en", "af", TranslationType.TEXT);
        save(ada, "Thank you", "Ngiyabonga", "en", "zu", TranslationType.VOICE);
        save(ada, "Where is the station?", "Où est la gare ?", "en", "fr", TranslationType.TEXT);
        save(ada, "100% sure_thing", "100% seker", "en", "af", TranslationType.TEXT);
        save(grace, "Good morning", "Buenos días", "en", "es", TranslationType.TEXT);
    }

    @Test
    @DisplayName("lists only the caller's translations, newest first, with paging metadata")
    void listsOwnHistory() throws Exception {
        perform(get("/api/translations/history"), adaToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[0].originalText").value("100% sure_thing"))
                .andExpect(jsonPath("$.items[3].originalText").value("Good morning"))
                .andExpect(jsonPath("$.items[*].translatedText", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("Buenos días"))));
    }

    @Test
    @DisplayName("search matches original or translated text, case-insensitively")
    void searches() throws Exception {
        perform(get("/api/translations/history").param("search", "MÔRE"), adaToken)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].originalText").value("Good morning"));
        perform(get("/api/translations/history").param("search", "station"), adaToken)
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @DisplayName("search treats % and _ literally rather than as wildcards")
    void searchEscapesWildcards() throws Exception {
        perform(get("/api/translations/history").param("search", "%"), adaToken)
                .andExpect(jsonPath("$.totalItems").value(1));
        perform(get("/api/translations/history").param("search", "o_d"), adaToken) // as a wildcard, _ would match "Good"
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    @DisplayName("filters by language (source or target) and by type; filters combine")
    void filters() throws Exception {
        perform(get("/api/translations/history").param("language", "af"), adaToken)
                .andExpect(jsonPath("$.totalItems").value(2));
        perform(get("/api/translations/history").param("type", "voice"), adaToken)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].translationType").value("VOICE"));
        perform(get("/api/translations/history").param("language", "af").param("search", "morning"), adaToken)
                .andExpect(jsonPath("$.totalItems").value(1));
        perform(get("/api/translations/history").param("language", "en").param("type", "TEXT"), adaToken)
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    @DisplayName("pages through results")
    void pages() throws Exception {
        perform(get("/api/translations/history").param("size", "3").param("page", "1"), adaToken)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("rejects invalid paging and filter values")
    void validatesParameters() throws Exception {
        perform(get("/api/translations/history").param("size", "500"), adaToken).andExpect(status().isBadRequest());
        perform(get("/api/translations/history").param("page", "-1"), adaToken).andExpect(status().isBadRequest());
        perform(get("/api/translations/history").param("type", "VIDEO"), adaToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.type").exists());
        perform(get("/api/translations/history").param("language", "en' OR 1=1 --"), adaToken)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /{id} returns the caller's record; another user's record is a 404, not a 403")
    void getIsScopedToOwner() throws Exception {
        long adaRecord = idOf(ada, "Thank you");
        perform(get("/api/translations/" + adaRecord), adaToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translatedText").value("Ngiyabonga"));
        perform(get("/api/translations/" + adaRecord), graceToken)
                .andExpect(status().isNotFound());
        perform(get("/api/translations/999999"), adaToken)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /{id} removes the caller's record but cannot touch another user's")
    void deleteIsScopedToOwner() throws Exception {
        long adaRecord = idOf(ada, "Thank you");

        perform(delete("/api/translations/" + adaRecord), graceToken).andExpect(status().isNotFound());
        assertThat(translationRepository.findById(adaRecord)).isPresent();

        perform(delete("/api/translations/" + adaRecord), adaToken).andExpect(status().isNoContent());
        assertThat(translationRepository.findById(adaRecord)).isEmpty();
        perform(delete("/api/translations/" + adaRecord), adaToken).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /history clears only the caller's history and reports the count")
    void clearIsScopedToOwner() throws Exception {
        perform(delete("/api/translations/history"), adaToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(4));

        assertThat(translationRepository.countByUserId(ada.getId())).isZero();
        assertThat(translationRepository.countByUserId(grace.getId())).isEqualTo(1);
        perform(get("/api/translations/history"), graceToken)
                .andExpect(jsonPath("$.items[*].translatedText", contains("Buenos días")));
    }

    @Test
    @DisplayName("history endpoints require authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/translations/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/translations/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/translations/1")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions perform(MockHttpServletRequestBuilder request,
                                                                      String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void save(User user, String original, String translated, String source, String target,
                      TranslationType type) {
        translationRepository.save(new Translation(user, original, translated, source, target, type));
    }

    private long idOf(User user, String originalText) {
        return translationRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId()) && t.getOriginalText().equals(originalText))
                .findFirst().orElseThrow().getId();
    }

    private String register(String name) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", name + " Tester", "email", name + "-" + UUID.randomUUID() + "@example.com",
                                "password", "Secret123", "confirmPassword", "Secret123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
