package com.linguavoice.integration.translation;

import com.linguavoice.integration.AiProviderException;
import java.util.Set;

/** Used when TRANSLATION_PROVIDER is not set (or has no key). Refuses honestly instead of faking output. */
public class NotConfiguredTranslationProvider implements TranslationProvider {

    private final String reason;

    public NotConfiguredTranslationProvider(String reason) {
        this.reason = reason;
    }

    /** Why translation is disabled; logged once at startup for the operator. */
    public String reason() {
        return reason;
    }

    @Override
    public String displayName() {
        return "Not configured";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public TranslationResult translate(String text, String sourceLanguage, String targetLanguage) {
        throw AiProviderException.notConfigured(FEATURE);
    }

    @Override
    public Set<String> supportedLanguages() {
        return Set.of();
    }
}
