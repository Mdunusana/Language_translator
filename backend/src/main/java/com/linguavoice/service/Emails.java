package com.linguavoice.service;

import java.util.Locale;

/** Email addresses are compared case-insensitively, so they are always stored and looked up normalised. */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
