package com.linguavoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * QA sweep: malformed input anywhere in the API must produce a client error (4xx) with the standard JSON
 * error body — never a 500, a stack trace or an HTML error page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiRobustnessTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void register() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Robust Tester", "email", "robust-" + UUID.randomUUID() + "@example.com",
                                "password", "Secret123", "confirmPassword", "Secret123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(body).get("accessToken").asText();
    }

    record BadRequest(String name, MockHttpServletRequestBuilder request) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<BadRequest> badRequests() {
        String json = MediaType.APPLICATION_JSON_VALUE;
        return Stream.of(
                new BadRequest("page is not a number", get("/api/translations/history").param("page", "abc")),
                new BadRequest("size is not a number", get("/api/translations/history").param("size", "1e9")),
                new BadRequest("days is not a number", get("/api/dashboard/statistics").param("days", "lots")),
                new BadRequest("limit is not a number", get("/api/dashboard/recent-activity").param("limit", "x")),
                new BadRequest("voices without language", get("/api/speech/voices")),
                new BadRequest("id is not a number", get("/api/translations/abc")),
                new BadRequest("id overflows long", get("/api/translations/99999999999999999999999")),
                new BadRequest("unknown route", get("/api/nope")),
                new BadRequest("wrong method", delete("/api/auth/login")),
                new BadRequest("empty JSON body", post("/api/translations/text").contentType(json)),
                new BadRequest("JSON array instead of object", post("/api/translations/text").contentType(json).content("[]")),
                new BadRequest("wrong JSON types", post("/api/speech/synthesize").contentType(json)
                        .content("{\"text\":{\"a\":1},\"language\":[\"zu\"],\"speed\":\"fast\"}")),
                new BadRequest("XML instead of JSON", put("/api/users/profile").contentType(MediaType.APPLICATION_XML)
                        .content("<user/>")),
                new BadRequest("form-encoded login", post("/api/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("email=a&password=b")),
                new BadRequest("voice upload as JSON", post("/api/translations/voice").contentType(json).content("{}")),
                new BadRequest("voice upload without file", multipart("/api/translations/voice")
                        .param("sourceLanguage", "en").param("targetLanguage", "zu")),
                new BadRequest("huge speech speed", post("/api/speech/synthesize").contentType(json)
                        .content("{\"text\":\"hi\",\"language\":\"zu\",\"speed\":1e308}")),
                new BadRequest("preferences with null body fields", put("/api/users/preferences").contentType(json)
                        .content("{\"preferredSourceLanguage\":null,\"preferredTargetLanguage\":null,\"speechSpeed\":null}")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badRequests")
    @DisplayName("malformed requests get a 4xx JSON error, never a 500")
    void malformedRequestsAreClientErrors(BadRequest bad) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(bad.request()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();

        assertThat(response.getStatus()).as("status for: " + bad.name()).isBetween(400, 499);
        assertThat(response.getContentType()).as("content type for: " + bad.name()).contains("application/json");
        assertThat(objectMapper.readTree(response.getContentAsString()).get("status").asInt())
                .isEqualTo(response.getStatus());
        assertThat(response.getContentAsString()).doesNotContain("Exception", "at com.", "at org.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingParameterMessages")
    @DisplayName("missing parameters are named in the message (not a misleading upload message)")
    void missingParameterMessage(String url, String expectedFragment) throws Exception {
        String body = mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).get("message").asText()).contains(expectedFragment);
    }

    static List<Object[]> missingParameterMessages() {
        return List.<Object[]>of(new Object[] {"/api/speech/voices", "Missing required parameter: language"});
    }
}
