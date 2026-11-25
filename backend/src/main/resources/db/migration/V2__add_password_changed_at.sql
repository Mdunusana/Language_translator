-- Tokens issued before this moment are rejected (see TokenRevocationValidator), so changing the password
-- signs out every other session. NULL means the password has never been changed.
ALTER TABLE users ADD COLUMN password_changed_at DATETIME(6) NULL;
