package com.linguavoice.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Verifies the Flyway V1 migration: tables exist and the key constraints are enforced. */
@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM translations");
        jdbc.update("DELETE FROM user_preferences");
        jdbc.update("DELETE FROM users");
    }

    @Test
    @DisplayName("V1 creates users, translations and user_preferences")
    void coreTablesExist() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE LOWER(table_name) IN ('users', 'translations', 'user_preferences')
                  AND LOWER(table_schema) <> 'information_schema'
                """, Integer.class);
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("Duplicate email addresses are rejected by the database")
    void duplicateEmailRejected() {
        insertUser("ada@example.com");
        assertThatThrownBy(() -> insertUser("ada@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("translation_type only accepts TEXT or VOICE")
    void translationTypeConstrained() {
        long userId = insertUser("grace@example.com");
        insertTranslation(userId, "TEXT");
        insertTranslation(userId, "VOICE");
        assertThatThrownBy(() -> insertTranslation(userId, "VIDEO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("A translation must belong to an existing user")
    void translationRequiresExistingUser() {
        assertThatThrownBy(() -> insertTranslation(999_999L, "TEXT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Deleting a user cascades to their translations and preferences")
    void deletingUserCascades() {
        long userId = insertUser("alan@example.com");
        insertTranslation(userId, "TEXT");
        jdbc.update("INSERT INTO user_preferences (user_id) VALUES (?)", userId);

        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM translations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_preferences", Integer.class)).isZero();
    }

    @Test
    @DisplayName("speech_speed is limited to 0.50 – 2.00")
    void speechSpeedConstrained() {
        long userId = insertUser("katherine@example.com");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_preferences (user_id, speech_speed) VALUES (?, ?)", userId, 3.5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertUser(String email) {
        jdbc.update("INSERT INTO users (full_name, email, password) VALUES (?, ?, ?)",
                "Test User", email, "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehol");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertTranslation(long userId, String type) {
        jdbc.update("""
                INSERT INTO translations (user_id, original_text, translated_text,
                                          source_language, target_language, translation_type)
                VALUES (?, 'Hello', 'Hallo', 'en', 'af', ?)
                """, userId, type);
    }
}
