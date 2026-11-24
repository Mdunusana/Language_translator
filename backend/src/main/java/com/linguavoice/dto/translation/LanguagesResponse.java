package com.linguavoice.dto.translation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * {@code GET /api/languages}.
 *
 * @param translationAvailable false when no provider is configured; {@code message} then says why
 * @param provider             attribution for the active translation provider
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LanguagesResponse(
        boolean translationAvailable,
        String provider,
        List<LanguageResponse> languages,
        String message) {

    /**
     * Capabilities per language. Voice input and output are reported separately because speech providers
     * support fewer languages than translation; they become true in Phases 4 and 5 when a provider supports them.
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
