package com.linguavoice.integration.translation;

import java.util.Set;

/**
 * Abstraction over a machine-translation service. The rest of the application only depends on this
 * interface, so providers can be swapped by configuration ({@code TRANSLATION_PROVIDER}).
 *
 * <p>All language codes crossing this interface are <b>LinguaVoice codes</b> (e.g. {@code zh} for Mandarin);
 * each implementation maps them to and from its provider's own codes.
 */
public interface TranslationProvider {

    String FEATURE = "Text translation";

    /** Human-readable provider name, shown as attribution in the UI (e.g. "Azure AI Translator"). */
    String displayName();

    /** False when no provider or credentials are configured; calls then fail with HTTP 503. */
    boolean isConfigured();

    /**
     * @param text           text to translate (already validated: non-blank, within limits)
     * @param sourceLanguage LinguaVoice code, or {@code null} to let the provider detect it
     * @param targetLanguage LinguaVoice code
     * @throws com.linguavoice.integration.AiProviderException on any provider failure
     */
    TranslationResult translate(String text, String sourceLanguage, String targetLanguage);

    /** LinguaVoice codes this provider can translate between. Implementations cache the provider's answer. */
    Set<String> supportedLanguages();
}
