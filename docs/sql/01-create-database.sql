-- One-time local setup. Run this as the MySQL root user (e.g. in MySQL Workbench),
-- AFTER replacing the placeholder password with a strong one of your own.
-- Put the same password in your local .env file as DB_PASSWORD. Never commit it.
--
-- Tables are NOT created here: Flyway creates and versions them when the backend starts
-- (backend/src/main/resources/db/migration).

CREATE DATABASE IF NOT EXISTS linguavoice_db
    CHARACTER SET utf8mb4          -- full Unicode: Mandarin, Arabic, Japanese, emoji
    COLLATE utf8mb4_0900_ai_ci;    -- case-insensitive, so the unique email index is case-insensitive too

CREATE USER IF NOT EXISTS 'linguavoice'@'localhost' IDENTIFIED BY 'REPLACE_WITH_A_STRONG_PASSWORD';

-- Least privilege: the application account can only touch its own database.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON linguavoice_db.* TO 'linguavoice'@'localhost';

FLUSH PRIVILEGES;
