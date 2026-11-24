package com.linguavoice.dto.translation;

import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * A saved translation as returned by the API.
 *
 * @param sourceDetected true when {@code sourceLanguage} was detected by the provider rather than chosen
 */
public record TranslationResponse(
        Long id,
        String originalText,
        String translatedText,
        String sourceLanguage,
        boolean sourceDetected,
        String targetLanguage,
        TranslationType translationType,
        Instant createdAt) {

    public static TranslationResponse from(Translation translation, boolean sourceDetected) {
        return new TranslationResponse(
                translation.getId(),
                translation.getOriginalText(),
                translation.getTranslatedText(),
                translation.getSourceLanguage(),
                sourceDetected,
                translation.getTargetLanguage(),
                translation.getTranslationType(),
                translation.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
