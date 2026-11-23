package com.linguavoice.dto;

/** Shared validation constants so the same rules (and messages) apply to every request DTO. */
public final class ValidationRules {

    public static final int PASSWORD_MIN = 8;
    /** BCrypt only uses the first 72 bytes; longer passwords would be silently truncated. */
    public static final int PASSWORD_MAX = 72;
    public static final String PASSWORD_LENGTH_MESSAGE = "Password must be between 8 and 72 characters.";
    /** At least one letter and one digit. */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String PASSWORD_PATTERN_MESSAGE = "Password must contain at least one letter and one number.";

    private ValidationRules() {
    }
}
