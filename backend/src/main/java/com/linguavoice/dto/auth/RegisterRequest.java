package com.linguavoice.dto.auth;

import com.linguavoice.dto.ValidationRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /api/auth/register}. Password confirmation is checked in the service. */
public record RegisterRequest(
        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters.")
        String fullName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email must be at most 255 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = ValidationRules.PASSWORD_MIN, max = ValidationRules.PASSWORD_MAX,
                message = ValidationRules.PASSWORD_LENGTH_MESSAGE)
        @Pattern(regexp = ValidationRules.PASSWORD_PATTERN, message = ValidationRules.PASSWORD_PATTERN_MESSAGE)
        String password,

        @NotBlank(message = "Please confirm your password.")
        String confirmPassword) {

    /** Trims before Bean Validation runs, so pasted values with stray spaces are accepted. */
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
    }

    @Override
    public String toString() {
        return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", password=***]";
    }
}
