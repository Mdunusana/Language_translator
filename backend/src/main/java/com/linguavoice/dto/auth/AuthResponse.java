package com.linguavoice.dto.auth;

import com.linguavoice.dto.user.UserResponse;
import java.time.Instant;

/** Returned after a successful login or registration. */
public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user) {

    public static AuthResponse bearer(String token, Instant expiresAt, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresAt, user);
    }
}
