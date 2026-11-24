package com.linguavoice.dto.translation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * {@code GET /api/languages}.
 *
 * @param translationAvailable false when no translation provider is configured; {@code message} says why
 * @param provider             attribution for the active translation provider
 * @param voiceInputProvider   attribution for the active speech-to-text provider, or null when none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LanguagesResponse(
        boolean translationAvailable,
        String provider,
        boolean voiceInputAvailable,
        String voiceInputProvider,
        List<LanguageResponse> languages,
        String message) {

    /**
     * Capabilities per language. Voice input and output are reported separately because speech providers
     * support fewer languages than translation (e.g. Azure Speech cannot recognise isiXhosa).
     */
    public record LanguageResponse(
            String code,
            String name,
            String nativeName,
            boolean textTranslation,
            boolean voiceInput,
            boolean voiceOutput) {
    }
}
