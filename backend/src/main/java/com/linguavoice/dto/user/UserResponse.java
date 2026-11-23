package com.linguavoice.dto.user;

import com.linguavoice.entity.User;
import com.linguavoice.entity.UserPreference;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

/** Public view of an account. Never includes the password hash. */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        Instant createdAt,
        Preferences preferences) {

    public record Preferences(
            String preferredSourceLanguage,
            String preferredTargetLanguage,
            BigDecimal speechSpeed) {
    }

    public static UserResponse from(User user, UserPreference preference) {
        Preferences preferences = preference == null ? null : new Preferences(
                preference.getPreferredSourceLanguage(),
                preference.getPreferredTargetLanguage(),
                preference.getSpeechSpeed());
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getCreatedAt().toInstant(ZoneOffset.UTC), preferences);
    }
}
