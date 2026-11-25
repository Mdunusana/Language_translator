package com.linguavoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Security QA: forged and malformed tokens are all rejected, nothing sensitive leaks into responses,
 * and every API response carries the defensive headers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningTest {

    /** The test profile's signing secret (application-test.yml). Used to prove claim checks, not just signatures. */
    private static final byte[] REAL_SECRET =
            "test-only-secret-not-used-anywhere-else-0123456789".getBytes(StandardCharsets.UTF_8);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private long userId;
    private String token;
    private String email;

    @BeforeEach
    void register() throws Exception {
        email = "sec-" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Security Tester", "email", email,
                                "password", "Secret123", "confirmPassword", "Secret123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        token = json.get("accessToken").asText();
        userId = json.get("user").get("id").asLong();
    }

    @Test
    @DisplayName("an unsigned token (alg: none) is rejected")
    void rejectsUnsignedToken() throws Exception {
        String unsigned = new PlainJWT(claims(String.valueOf(userId), "linguavoice")).serialize();
        profileWith(unsigned).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token signed with another key is rejected")
    void rejectsForeignKey() throws Exception {
        profileWith(sign(claims(String.valueOf(userId), "linguavoice"),
                "an-attackers-own-secret-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("editing the payload (changing the user id) breaks the signature")
    void rejectsEditedPayload() throws Exception {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"sub\":\"" + userId + "\"", "\"sub\":\"" + (userId + 1) + "\"");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        profileWith(forged).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("correctly signed tokens with a bad subject or for a missing account are rejected")
    void rejectsBadSubjects() throws Exception {
        profileWith(sign(claims("not-a-number", "linguavoice"), REAL_SECRET)).andExpect(status().isUnauthorized());
        profileWith(sign(claims("999999999", "linguavoice"), REAL_SECRET)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("garbage in the Authorization header is a clean 401")
    void rejectsGarbage() throws Exception {
        for (String value : new String[] {"Bearer", "Bearer ", "Bearer a.b.c", "Bearer " + "x".repeat(5000), "Basic dXNlcjpwYXNz"}) {
            mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, value))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("API responses carry CSP, no-referrer, nosniff, frame denial and no-store caching")
    void securityHeaders() throws Exception {
        profileWith(token)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    @DisplayName("no response ever contains the password or its hash")
    void neverLeaksPasswordHash() throws Exception {
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Secret123"))))
                .andReturn().getResponse().getContentAsString();
        String profile = profileWith(token).andReturn().getResponse().getContentAsString();
        for (String body : new String[] {login, profile}) {
            assertThat(body).doesNotContain("$2a$").doesNotContain("Secret123").doesNotContainIgnoringCase("password\"");
        }
    }

    private ResultActions profileWith(String bearerToken) throws Exception {
        return mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken));
    }

    private static JWTClaimsSet claims(String subject, String issuer) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().subject(subject).issuer(issuer)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600))).build();
    }

    private static String sign(JWTClaimsSet claims, byte[] secret) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }
}
