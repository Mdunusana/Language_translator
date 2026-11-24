package com.linguavoice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import com.linguavoice.entity.User;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

    private static final ZoneId JOHANNESBURG = ZoneId.of("Africa/Johannesburg");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferenceRepository preferenceRepository;
    @Autowired private TranslationRepository translationRepository;
    @Autowired private JdbcTemplate jdbc;

    private String token;
    private User ada;

    @BeforeEach
    void setUp() throws Exception {
        translationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();
        token = register("ada");
        String otherToken = register("grace");
        ada = userRepository.findAll().stream().filter(u -> u.getEmail().startsWith("ada")).findFirst().orElseThrow();
        User grace = userRepository.findAll().stream().filter(u -> u.getEmail().startsWith("grace")).findFirst().orElseThrow();

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        save(ada, "en", "af", TranslationType.TEXT, now.minusHours(1));
        save(ada, "en", "zu", TranslationType.VOICE, now.minusHours(2));
        save(ada, "af", "en", TranslationType.TEXT, now.minusDays(3));
        save(ada, "und", "en", TranslationType.TEXT, now.minusDays(10));     // undetermined source is not a "language"
        save(ada, "en", "af", TranslationType.VOICE, now.minusDays(40));     // outside 7 and 14 days
        save(grace, "en", "es", TranslationType.TEXT, now.minusHours(1));   // another user: never counted
        Assertions.assertThat(otherToken).isNotBlank();
    }

    @Test
    @DisplayName("counts totals, types and the last 7 days for the caller only")
    void counts() throws Exception {
        statistics(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTranslations").value(5))
                .andExpect(jsonPath("$.textTranslations").value(3))
                .andExpect(jsonPath("$.voiceTranslations").value(2))
                .andExpect(jsonPath("$.lastSevenDays").value(3));
    }

    @Test
    @DisplayName("ranks languages by use as source or target, ignoring 'und' and other users")
    void topLanguages() throws Exception {
        // en: 3 as source + 2 as target = 5; af: 1 + 2 = 3; zu: 1. Total uses = 9 (und excluded) → en = 56%.
        statistics(null)
                .andExpect(jsonPath("$.topLanguages[0].code").value("en"))
                .andExpect(jsonPath("$.topLanguages[0].count").value(5))
                .andExpect(jsonPath("$.topLanguages[0].share").value(56))
                .andExpect(jsonPath("$.topLanguages[1].code").value("af"))
                .andExpect(jsonPath("$.topLanguages[1].name").value("Afrikaans"))
                .andExpect(jsonPath("$.topLanguages[2].code").value("zu"))
                .andExpect(jsonPath("$.topLanguages.length()").value(3));
    }

    @Test
    @DisplayName("daily activity has one zero-filled entry per day, oldest first, ending today")
    void dailyActivityIsZeroFilled() throws Exception {
        JsonNode body = json(statistics("UTC").andReturn().getResponse().getContentAsString());
        JsonNode days = body.get("dailyActivity");

        Assertions.assertThat(days.size()).isEqualTo(14);
        Assertions.assertThat(days.get(13).get("date").asText()).isEqualTo(LocalDate.now(ZoneOffset.UTC).toString());
        long sum = 0;
        for (JsonNode day : days) {
            sum += day.get("count").asLong();
        }
        Assertions.assertThat(sum).isEqualTo(4); // the 40-day-old record is outside the window
    }

    @Test
    @DisplayName("buckets days in the caller's time zone, not UTC")
    void dailyActivityUsesTimeZone() throws Exception {
        translationRepository.deleteAll();
        // One minute after midnight in Johannesburg (UTC+2) is still the previous day in UTC.
        ZonedDateTime local = ZonedDateTime.now(JOHANNESBURG).toLocalDate().atStartOfDay(JOHANNESBURG).plusMinutes(1);
        if (local.isAfter(ZonedDateTime.now(JOHANNESBURG))) {
            local = local.minusDays(1);
        }
        save(ada, "en", "af", TranslationType.TEXT,
                local.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
        LocalDate localDay = local.toLocalDate();

        JsonNode inJohannesburg = json(statistics("Africa/Johannesburg").andReturn().getResponse().getContentAsString());
        JsonNode inUtc = json(statistics("UTC").andReturn().getResponse().getContentAsString());

        Assertions.assertThat(countOn(inJohannesburg, localDay)).isEqualTo(1);
        Assertions.assertThat(countOn(inUtc, localDay)).isZero();
        Assertions.assertThat(countOn(inUtc, localDay.minusDays(1))).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects unknown time zones and out-of-range day counts")
    void validatesParameters() throws Exception {
        statistics("Mars/Olympus_Mons").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.timezone").exists());
        mockMvc.perform(get("/api/dashboard/statistics").param("days", "1000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("recent activity returns the caller's newest translations")
    void recentActivity() throws Exception {
        mockMvc.perform(get("/api/dashboard/recent-activity").param("limit", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].targetLanguage").value("af"))
                .andExpect(jsonPath("$[1].targetLanguage").value("zu"));
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard/statistics")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions statistics(String timeZone) throws Exception {
        var request = get("/api/dashboard/statistics").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (timeZone != null) {
            request.param("timezone", timeZone);
        }
        return mockMvc.perform(request);
    }

    private static long countOn(JsonNode statistics, LocalDate date) {
        for (JsonNode day : statistics.get("dailyActivity")) {
            if (day.get("date").asText().equals(date.toString())) {
                return day.get("count").asLong();
            }
        }
        return -1;
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private void save(User user, String source, String target, TranslationType type, LocalDateTime createdAtUtc) {
        Translation saved = translationRepository.save(new Translation(user, "text", "translated", source, target, type));
        jdbc.update("UPDATE translations SET created_at = ? WHERE id = ?", createdAtUtc, saved.getId());
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
