package com.linguavoice.dto.translation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/translations/text}. Whether a language is actually supported is checked in the service,
 * against the configured provider.
 *
 * @param sourceLanguage a language code, or {@code "auto"}/null/blank for automatic detection
 */
public record TextTranslationRequest(
        @NotBlank(message = "Enter some text to translate.")
        @Size(max = TextTranslationRequest.MAX_TEXT_LENGTH,
                message = "Text can be at most " + TextTranslationRequest.MAX_TEXT_LENGTH + " characters.")
        String text,

        @Pattern(regexp = LANGUAGE_CODE + "|auto", message = "Unknown source language.")
        String sourceLanguage,

        @NotBlank(message = "Choose a target language.")
        @Pattern(regexp = LANGUAGE_CODE, message = "Unknown target language.")
        String targetLanguage) {

    public static final int MAX_TEXT_LENGTH = 5000;
    static final String LANGUAGE_CODE = "^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$";
    public static final String AUTO_DETECT = "auto";

    /** Trims surrounding whitespace before validation; an empty source means auto-detect. */
    public TextTranslationRequest {
        text = text == null ? null : text.strip();
        sourceLanguage = sourceLanguage == null || sourceLanguage.isBlank() ? AUTO_DETECT : sourceLanguage.trim();
        targetLanguage = targetLanguage == null ? null : targetLanguage.trim();
    }

    public boolean autoDetect() {
        return AUTO_DETECT.equals(sourceLanguage);
    }
}
