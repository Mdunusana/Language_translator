package com.linguavoice.service;

import com.linguavoice.dto.translation.LanguagesResponse;
import com.linguavoice.dto.translation.LanguagesResponse.LanguageResponse;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.speech.SpeechToTextProvider;
import com.linguavoice.integration.translation.TranslationProvider;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Decides which languages the app advertises, and for which features: those in {@link LanguageCatalog}
 * that the configured providers report as supported. Nothing is offered on the strength of the catalog alone.
 */
@Service
public class LanguageService {

    static final String NOT_CONFIGURED_MESSAGE =
            "Text translation is not configured on this server yet. An administrator needs to add a translation "
                    + "provider key.";

    private final TranslationProvider translationProvider;
    private final SpeechToTextProvider speechToTextProvider;
    private final SpeechService speechService;

    public LanguageService(TranslationProvider translationProvider, SpeechToTextProvider speechToTextProvider,
                           SpeechService speechService) {
        this.translationProvider = translationProvider;
        this.speechToTextProvider = speechToTextProvider;
        this.speechService = speechService;
    }

    public LanguagesResponse listLanguages() {
        if (!translationProvider.isConfigured()) {
            return new LanguagesResponse(false, null, false, null, false, null, List.of(), NOT_CONFIGURED_MESSAGE);
        }
        Set<String> translatable = translationProvider.supportedLanguages();
        Set<String> voiceInput = speechToTextProvider.isConfigured() ? speechToTextProvider.supportedLanguages() : Set.of();
        Set<String> voiceOutput = speechService.languagesWithVoices();

        List<LanguageResponse> languages = LanguageCatalog.LANGUAGES.stream()
                .filter(language -> translatable.contains(language.code()))
                .map(language -> new LanguageResponse(language.code(), language.name(), language.nativeName(),
                        true, voiceInput.contains(language.code()), voiceOutput.contains(language.code())))
                .toList();
        boolean inputAvailable = languages.stream().anyMatch(LanguageResponse::voiceInput);
        boolean outputAvailable = languages.stream().anyMatch(LanguageResponse::voiceOutput);
        return new LanguagesResponse(true, translationProvider.displayName(),
                inputAvailable, inputAvailable ? speechToTextProvider.displayName() : null,
                outputAvailable, outputAvailable ? speechService.providerName() : null,
                languages, null);
    }

    /**
     * Codes that can currently be used for text translation, in catalog order.
     *
     * @throws AiProviderException 503 when no translation provider is configured
     */
    public Set<String> translatableLanguageCodes() {
        if (!translationProvider.isConfigured()) {
            throw AiProviderException.notConfigured(TranslationProvider.FEATURE);
        }
        Set<String> supported = translationProvider.supportedLanguages();
        Set<String> codes = new LinkedHashSet<>();
        LanguageCatalog.LANGUAGES.stream()
                .map(LanguageCatalog.Language::code)
                .filter(supported::contains)
                .forEach(codes::add);
        return codes;
    }

    /**
     * Codes that can be spoken into the server-side recogniser (and then translated).
     *
     * @throws AiProviderException 503 when no speech-to-text provider is configured
     */
    public Set<String> voiceInputLanguageCodes() {
        if (!speechToTextProvider.isConfigured()) {
            throw AiProviderException.notConfigured(SpeechToTextProvider.FEATURE);
        }
        Set<String> codes = new LinkedHashSet<>(translatableLanguageCodes());
        codes.retainAll(speechToTextProvider.supportedLanguages());
        return codes;
    }

    /** Display name for messages, e.g. "isiXhosa". */
    public static String nameOf(String code) {
        return LanguageCatalog.LANGUAGES.stream()
                .filter(language -> language.code().equals(code))
                .map(LanguageCatalog.Language::name)
                .findFirst()
                .orElse(code);
    }
}
