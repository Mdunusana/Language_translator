package com.linguavoice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/health is public and reports the database as UP")
    void healthIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("LinguaVoice"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    @DisplayName("Protected endpoints return a JSON 401 when no credentials are sent")
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/translations/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/json")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/translations/history"));
    }

    @Test
    @DisplayName("CORS preflight from the configured frontend origin is allowed")
    void corsPreflightAllowedForFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5500")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5500"));
    }

    @Test
    @DisplayName("CORS preflight from an unknown origin is rejected")
    void corsPreflightRejectedForUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
