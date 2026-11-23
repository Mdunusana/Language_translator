package com.linguavoice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/auth/login}. Only presence is validated so the response never reveals password rules. */
public record LoginRequest(
        @NotBlank(message = "Email is required.")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Password is required.")
        @Size(max = 200)
        String password) {

    public LoginRequest {
        email = email == null ? null : email.trim();
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
