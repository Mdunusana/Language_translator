package com.linguavoice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    private static final String PASSWORD = "Secret123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferenceRepository preferenceRepository;
    @Autowired private TranslationRepository translationRepository;

    private String adaToken;
    private String graceToken;

    @BeforeEach
    void setUp() throws Exception {
        translationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();
        adaToken = registerAndGetToken("Ada Lovelace", "ada@example.com");
        graceToken = registerAndGetToken("Grace Hopper", "grace@example.com");
    }

    @Test
    @DisplayName("GET /api/users/profile without a token returns 401")
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("each user only ever sees their own profile")
    void profileIsScopedToTokenOwner() throws Exception {
        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, bearer(adaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.preferences.speechSpeed").value(1.00));
        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, bearer(graceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("grace@example.com"));
    }

    @Test
    @DisplayName("PUT /api/users/profile updates name and normalised email")
    void updatesProfile() throws Exception {
        updateProfile(adaToken, "Augusta Ada King", " Countess@Example.com ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Augusta Ada King"))
                .andExpect(jsonPath("$.email").value("countess@example.com"));

        login("countess@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/users/profile keeps the same email without a conflict")
    void keepsOwnEmail() throws Exception {
        updateProfile(adaToken, "Ada L.", "ada@example.com").andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/users/profile cannot take another user's email (409)")
    void rejectsEmailOfAnotherUser() throws Exception {
        updateProfile(adaToken, "Ada Lovelace", "GRACE@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("PUT /api/users/profile validates input")
    void validatesProfile() throws Exception {
        updateProfile(adaToken, "", "nope")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("PUT /api/users/password changes the password: new one works, old one no longer does")
    void changesPassword() throws Exception {
        changePassword(adaToken, PASSWORD, "NewSecret456", "NewSecret456").andExpect(status().isNoContent());

        login("ada@example.com", "NewSecret456").andExpect(status().isOk());
        login("ada@example.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/users/password requires the correct current password")
    void rejectsWrongCurrentPassword() throws Exception {
        changePassword(adaToken, "Wrong12345", "NewSecret456", "NewSecret456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").value("Current password is incorrect."));
        login("ada@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/users/password rejects mismatch, weak and unchanged passwords")
    void validatesNewPassword() throws Exception {
        changePassword(adaToken, PASSWORD, "NewSecret456", "Other456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.confirmNewPassword").exists());
        changePassword(adaToken, PASSWORD, "weak", "weak")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        changePassword(adaToken, PASSWORD, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    @Test
    @DisplayName("PUT /api/users/preferences saves languages and speed and returns them")
    void updatesPreferences() throws Exception {
        updatePreferences(adaToken, "auto", "zu", "1.25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.preferredSourceLanguage").value("auto"))
                .andExpect(jsonPath("$.preferences.preferredTargetLanguage").value("zu"))
                .andExpect(jsonPath("$.preferences.speechSpeed").value(1.25));

        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, bearer(adaToken)))
                .andExpect(jsonPath("$.preferences.preferredTargetLanguage").value("zu"));
        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, bearer(graceToken)))
                .andExpect(jsonPath("$.preferences.preferredTargetLanguage").value("af"));
    }

    @Test
    @DisplayName("PUT /api/users/preferences validates languages and speed")
    void validatesPreferences() throws Exception {
        updatePreferences(adaToken, "en", "klingon", "1.0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.preferredTargetLanguage").exists());
        updatePreferences(adaToken, "ko", "en", "1.0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.preferredSourceLanguage").value("Unknown language."));
        updatePreferences(adaToken, "en", "en", "1.0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.preferredTargetLanguage").exists());
        updatePreferences(adaToken, "en", "af", "2.5")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.speechSpeed").exists());
        updatePreferences(adaToken, "en", "af", "1.234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.speechSpeed").exists());
    }

    private ResultActions updatePreferences(String token, String source, String target, String speed) throws Exception {
        return mockMvc.perform(put("/api/users/preferences")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredSourceLanguage\":\"" + source + "\",\"preferredTargetLanguage\":\"" + target
                        + "\",\"speechSpeed\":" + speed + "}"));
    }

    private ResultActions updateProfile(String token, String fullName, String email) throws Exception {
        return mockMvc.perform(put("/api/users/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fullName", fullName, "email", email))));
    }

    private ResultActions changePassword(String token, String current, String next, String confirm) throws Exception {
        return mockMvc.perform(put("/api/users/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "currentPassword", current, "newPassword", next, "confirmNewPassword", confirm))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String registerAndGetToken(String fullName, String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", fullName, "email", email,
                                "password", PASSWORD, "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
