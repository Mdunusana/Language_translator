package com.linguavoice.service;

import com.linguavoice.dto.translation.LanguagesResponse;
import com.linguavoice.dto.translation.LanguagesResponse.LanguageResponse;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.translation.TranslationProvider;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Decides which languages the app advertises: those in {@link LanguageCatalog} that the configured
 * provider reports as supported. Nothing is offered on the strength of the catalog alone.
 */
@Service
public class LanguageService {

    static final String NOT_CONFIGURED_MESSAGE =
            "Text translation is not configured on this server yet. An administrator needs to add a translation "
                    + "provider key.";

    private final TranslationProvider translationProvider;

    public LanguageService(TranslationProvider translationProvider) {
        this.translationProvider = translationProvider;
    }

    public LanguagesResponse listLanguages() {
        if (!translationProvider.isConfigured()) {
            return new LanguagesResponse(false, null, List.of(), NOT_CONFIGURED_MESSAGE);
        }
        Set<String> supported = translationProvider.supportedLanguages();
        List<LanguageResponse> languages = LanguageCatalog.LANGUAGES.stream()
                .filter(language -> supported.contains(language.code()))
                .map(language -> new LanguageResponse(language.code(), language.name(), language.nativeName(),
                        true, false, false))
                .toList();
        return new LanguagesResponse(true, translationProvider.displayName(), languages, null);
    }

    /**
     * Codes that can currently be used for text translation, in catalog order.
     *
     * @throws AiProviderException 503 when no provider is configured
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
}
