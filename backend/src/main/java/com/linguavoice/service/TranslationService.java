package com.linguavoice.service;

import com.linguavoice.dto.translation.TextTranslationRequest;
import com.linguavoice.dto.translation.TranslationResponse;
import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import com.linguavoice.entity.User;
import com.linguavoice.exception.ApiException;
import com.linguavoice.integration.translation.TranslationProvider;
import com.linguavoice.integration.translation.TranslationResult;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserRepository;
import com.linguavoice.security.RateLimiter;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Text translation: validate → rate-limit → call the provider → save to the user's history.
 *
 * <p>Deliberately not {@code @Transactional}: the provider call can take seconds, and holding a database
 * connection open during it would exhaust the pool under load. The single insert runs in its own transaction.
 */
@Service
public class TranslationService {

    private final TranslationProvider translationProvider;
    private final LanguageService languageService;
    private final TranslationRepository translationRepository;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;

    public TranslationService(TranslationProvider translationProvider, LanguageService languageService,
                              TranslationRepository translationRepository, UserRepository userRepository,
                              RateLimiter rateLimiter) {
        this.translationProvider = translationProvider;
        this.languageService = languageService;
        this.translationRepository = translationRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
    }

    public TranslationResponse translateText(long userId, TextTranslationRequest request) {
        Set<String> supported = languageService.translatableLanguageCodes();
        validateLanguages(request, supported);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Your session is no longer valid. Please sign in again."));
        rateLimiter.consumeAiRequest(userId);

        String requestedSource = request.autoDetect() ? null : request.sourceLanguage();
        TranslationResult result = translationProvider.translate(request.text(), requestedSource,
                request.targetLanguage());

        String sourceLanguage = requestedSource != null ? requestedSource
                : result.detectedSourceLanguage() != null ? result.detectedSourceLanguage()
                : LanguageCatalog.UNDETERMINED;

        Translation saved = translationRepository.save(new Translation(user, request.text(),
                result.translatedText(), sourceLanguage, request.targetLanguage(), TranslationType.TEXT));
        return TranslationResponse.from(saved, requestedSource == null);
    }

    private static void validateLanguages(TextTranslationRequest request, Set<String> supported) {
        if (!supported.contains(request.targetLanguage())) {
            throw ApiException.invalidField("targetLanguage", "This target language is not supported.");
        }
        if (!request.autoDetect()) {
            if (!supported.contains(request.sourceLanguage())) {
                throw ApiException.invalidField("sourceLanguage", "This source language is not supported.");
            }
            if (request.sourceLanguage().equals(request.targetLanguage())) {
                throw ApiException.invalidField("targetLanguage",
                        "Choose a target language that is different from the source language.");
            }
        }
    }
}
