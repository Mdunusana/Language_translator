package com.linguavoice.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.linguavoice.entity.User;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Regression test: timestamps must be stored as real UTC regardless of the server's time zone.
 * Previously hibernate.jdbc.time_zone=UTC made Hibernate convert LocalDateTime through the JVM zone,
 * so on a UTC+2 machine every stored value was 2 hours early (invisible to the app, wrong in the database).
 */
@SpringBootTest
@ActiveProfiles("test")
class TimestampStorageTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferenceRepository preferenceRepository;
    @Autowired private TranslationRepository translationRepository;
    @Autowired private JdbcTemplate jdbc;

    private TimeZone originalZone;

    @BeforeEach
    void setUp() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Johannesburg")); // a non-UTC server, like the dev machine
        translationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    @DisplayName("created_at in the database equals the real UTC time, even on a UTC+2 server")
    void storesRealUtc() {
        User user = userRepository.save(new User("Clock Tester", "clock@example.com", "$2a$hash"));
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        LocalDateTime stored = jdbc.queryForObject(
                "SELECT created_at FROM users WHERE id = ?", LocalDateTime.class, user.getId());

        assertThat(Duration.between(stored, nowUtc).abs()).isLessThan(Duration.ofMinutes(1));
    }
}
