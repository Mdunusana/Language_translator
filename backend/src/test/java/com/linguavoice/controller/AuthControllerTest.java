package com.linguavoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguavoice.entity.User;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String PASSWORD = "Secret123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferenceRepository preferenceRepository;
    @Autowired private TranslationRepository translationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtEncoder jwtEncoder;

    @BeforeEach
    void cleanDatabase() {
        translationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("creates the account, default preferences and returns a token")
        void registersUser() throws Exception {
            register("Ada Lovelace", "Ada@Example.com", PASSWORD, PASSWORD)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                    .andExpect(jsonPath("$.user.fullName").value("Ada Lovelace"))
                    .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                    .andExpect(jsonPath("$.user.preferences.preferredSourceLanguage").value("en"))
                    .andExpect(jsonPath("$.user.password").doesNotExist())
                    .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

            User saved = userRepository.findByEmail("ada@example.com").orElseThrow();
            assertThat(preferenceRepository.findByUserId(saved.getId())).isPresent();
        }

        @Test
        @DisplayName("stores a BCrypt hash, never the plain-text password")
        void hashesPassword() throws Exception {
            register("Ada Lovelace", "ada@example.com", PASSWORD, PASSWORD).andExpect(status().isCreated());

            String stored = userRepository.findByEmail("ada@example.com").orElseThrow().getPasswordHash();
            assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2a$");
            assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
        }

        @Test
        @DisplayName("rejects a duplicate email regardless of letter case with 409")
        void rejectsDuplicateEmail() throws Exception {
            register("Ada Lovelace", "ada@example.com", PASSWORD, PASSWORD).andExpect(status().isCreated());

            register("Someone Else", "  ADA@example.COM ", PASSWORD, PASSWORD)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());
            assertThat(userRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("validates every field and reports errors per field")
        void validatesInput() throws Exception {
            register("A", "not-an-email", "short", "")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.password").exists())
                    .andExpect(jsonPath("$.fieldErrors.confirmPassword").exists());
            assertThat(userRepository.count()).isZero();
        }

        @Test
        @DisplayName("requires a password with letters and numbers")
        void enforcesPasswordStrength() throws Exception {
            register("Ada Lovelace", "ada@example.com", "onlyletters", "onlyletters")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").value(
                            "Password must contain at least one letter and one number."));
        }

        @Test
        @DisplayName("rejects mismatched password confirmation")
        void rejectsPasswordMismatch() throws Exception {
            register("Ada Lovelace", "ada@example.com", PASSWORD, "Different123")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.confirmPassword").value("Passwords do not match."));
        }

        @Test
        @DisplayName("rejects a malformed JSON body with 400")
        void rejectsMalformedJson() throws Exception {
            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @BeforeEach
        void createAccount() throws Exception {
            register("Ada Lovelace", "ada@example.com", PASSWORD, PASSWORD).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("returns a token for correct credentials (email is case-insensitive)")
        void logsIn() throws Exception {
            login("ADA@example.com", PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value("ada@example.com"));
        }

        @Test
        @DisplayName("wrong password and unknown email get the same 401 message")
        void rejectsBadCredentialsWithoutRevealingWhichPartIsWrong() throws Exception {
            String wrongPassword = login("ada@example.com", "Wrong12345")
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            String unknownEmail = login("nobody@example.com", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            assertThat(objectMapper.readTree(wrongPassword).get("message").asText())
                    .isEqualTo(objectMapper.readTree(unknownEmail).get("message").asText())
                    .isEqualTo("Invalid email or password.");
        }

        @Test
        @DisplayName("rejects empty credentials with 400")
        void validatesInput() throws Exception {
            login("", "")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());
        }
    }

    @Nested
    @DisplayName("Access tokens")
    class Tokens {

        @Test
        @DisplayName("a token from login grants access to protected endpoints")
        void tokenGrantsAccess() throws Exception {
            String token = registerAndGetToken();
            mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("ada@example.com"));
        }

        @Test
        @DisplayName("a tampered token is rejected with 401")
        void rejectsTamperedToken() throws Exception {
            String token = registerAndGetToken();
            String tampered = token.substring(0, token.length() - 4) + "AAAA";
            mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("an expired token is rejected with 401")
        void rejectsExpiredToken() throws Exception {
            registerAndGetToken();
            Long userId = userRepository.findByEmail("ada@example.com").orElseThrow().getId();
            String expired = signToken(userId, "linguavoice", Instant.now().minus(2, ChronoUnit.HOURS));

            mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a correctly signed token from a different issuer is rejected with 401")
        void rejectsWrongIssuer() throws Exception {
            registerAndGetToken();
            Long userId = userRepository.findByEmail("ada@example.com").orElseThrow().getId();
            String foreign = signToken(userId, "someone-else", Instant.now().plus(1, ChronoUnit.HOURS));

            mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a valid token for a deleted account is rejected with 401")
        void rejectsTokenOfDeletedUser() throws Exception {
            String token = registerAndGetToken();
            preferenceRepository.deleteAll();
            userRepository.deleteAll();

            mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        private String signToken(Long userId, String issuer, Instant expiresAt) {
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(String.valueOf(userId))
                    .issuedAt(expiresAt.minus(1, ChronoUnit.HOURS))
                    .expiresAt(expiresAt)
                    .build();
            return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                    .getTokenValue();
        }
    }

    private String registerAndGetToken() throws Exception {
        String body = register("Ada Lovelace", "ada@example.com", PASSWORD, PASSWORD)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private ResultActions register(String fullName, String email, String password, String confirm) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", fullName, "email", email, "password", password, "confirmPassword", confirm))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }
}
