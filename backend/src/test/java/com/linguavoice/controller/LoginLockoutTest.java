package com.linguavoice.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
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

/** Brute-force protection on POST /api/auth/login. Each test uses its own email so the shared limiter can't interfere. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginLockoutTest {

    private static final String PASSWORD = "Secret123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("after 5 wrong passwords even the right password is refused with 429 and Retry-After")
    void locksAccountAfterRepeatedFailures() throws Exception {
        String email = register();
        for (int i = 0; i < 5; i++) {
            login(email, "Wrong-" + i + "x1").andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message", startsWith("Too many failed sign-in attempts.")));
    }

    @Test
    @DisplayName("email letter case can't be used to dodge the lock")
    void lockIgnoresEmailCase() throws Exception {
        String email = register();
        for (int i = 0; i < 5; i++) {
            login(i % 2 == 0 ? email.toUpperCase() : email, "Wrong-" + i + "x1").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("unknown emails lock the same way, so lockouts don't reveal which emails are registered")
    void unknownEmailsBehaveTheSame() throws Exception {
        String unknown = "nobody-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 5; i++) {
            login(unknown, "Wrong-" + i + "x1").andExpect(status().isUnauthorized());
        }
        login(unknown, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("a successful sign-in resets the failure count")
    void successResets() throws Exception {
        String email = register();
        for (int i = 0; i < 4; i++) {
            login(email, "Wrong-" + i + "x1").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(email, "Wrong-" + i + "x1").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isOk());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String register() throws Exception {
        String email = "lockout-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Lockout Tester", "email", email,
                                "password", PASSWORD, "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated());
        return email;
    }
}
