package com.linguavoice.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the authenticated user's id from a validated access token.
 * Controllers pass this id to services, which scope every query by it.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
