package com.linguavoice.dto.user;

import com.linguavoice.dto.ValidationRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code PUT /api/users/password}. */
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required.")
        @Size(max = 200)
        String currentPassword,

        @NotBlank(message = "New password is required.")
        @Size(min = ValidationRules.PASSWORD_MIN, max = ValidationRules.PASSWORD_MAX,
                message = ValidationRules.PASSWORD_LENGTH_MESSAGE)
        @Pattern(regexp = ValidationRules.PASSWORD_PATTERN, message = ValidationRules.PASSWORD_PATTERN_MESSAGE)
        String newPassword,

        @NotBlank(message = "Please confirm your new password.")
        String confirmNewPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
