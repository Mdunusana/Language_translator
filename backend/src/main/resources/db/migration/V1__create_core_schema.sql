-- LinguaVoice core schema (users, translations, user_preferences).
--
-- Portability note: this script is written in the subset of SQL shared by MySQL 8 and H2
-- (MySQL mode), so the automated tests run the exact same migration as production.
-- Character set / collation (utf8mb4) is set at database level — see docs/sql/01-create-database.sql.
-- `updated_at` is maintained by the JPA entities (@PreUpdate) rather than ON UPDATE triggers.

CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    full_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    password    VARCHAR(100) NOT NULL,              -- BCrypt hash (60 chars); never plain text
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE translations (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    original_text    TEXT        NOT NULL,
    translated_text  TEXT        NOT NULL,
    source_language  VARCHAR(16) NOT NULL,             -- BCP-47 code, e.g. en, af, zu, xh, zh-CN
    target_language  VARCHAR(16) NOT NULL,
    translation_type VARCHAR(10) NOT NULL,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_translations PRIMARY KEY (id),
    CONSTRAINT fk_translations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_translations_type CHECK (translation_type IN ('TEXT', 'VOICE'))
);

-- History is always read per user, newest first; language filters are per user too.
CREATE INDEX idx_translations_user_created ON translations (user_id, created_at);
CREATE INDEX idx_translations_user_source  ON translations (user_id, source_language);
CREATE INDEX idx_translations_user_target  ON translations (user_id, target_language);

CREATE TABLE user_preferences (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                   BIGINT       NOT NULL,
    preferred_source_language VARCHAR(16)  NOT NULL DEFAULT 'en',
    preferred_target_language VARCHAR(16)  NOT NULL DEFAULT 'af',
    speech_speed              DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    created_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_preferences PRIMARY KEY (id),
    CONSTRAINT uk_user_preferences_user UNIQUE (user_id),   -- one preferences row per user
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_preferences_speed CHECK (speech_speed BETWEEN 0.50 AND 2.00)
);
