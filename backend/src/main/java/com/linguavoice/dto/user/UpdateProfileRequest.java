package com.linguavoice.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code PUT /api/users/profile}. Preferences get their own update in Phase 6. */
public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters.")
        String fullName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email must be at most 255 characters.")
        String email) {

    /** Trims before Bean Validation runs, so pasted values with stray spaces are accepted. */
    public UpdateProfileRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
    }
}
