package com.linguavoice.service;

import com.linguavoice.dto.translation.TextTranslationRequest;
import com.linguavoice.dto.translation.TranslationResponse;
import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import com.linguavoice.entity.User;
import com.linguavoice.exception.ApiException;
import com.linguavoice.integration.speech.SpeechToTextProvider;
import com.linguavoice.integration.speech.WavAudio;
import com.linguavoice.integration.translation.TranslationProvider;
import com.linguavoice.integration.translation.TranslationResult;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserRepository;
import com.linguavoice.security.RateLimiter;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Text and voice translation: validate → rate-limit → call the provider(s) → save to the user's history.
 *
 * <p>Deliberately not {@code @Transactional}: provider calls can take seconds, and holding a database
 * connection open during them would exhaust the pool under load. The single insert runs in its own transaction.
 */
@Service
public class TranslationService {

    static final String NO_SPEECH_MESSAGE =
            "We couldn't make out any speech in that recording. Try again, speaking clearly and close to the microphone.";
    private static final Pattern LANGUAGE_CODE = Pattern.compile("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$");

    private final TranslationProvider translationProvider;
    private final SpeechToTextProvider speechToTextProvider;
    private final LanguageService languageService;
    private final TranslationRepository translationRepository;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;

    public TranslationService(TranslationProvider translationProvider, SpeechToTextProvider speechToTextProvider,
                              LanguageService languageService, TranslationRepository translationRepository,
                              UserRepository userRepository, RateLimiter rateLimiter) {
        this.translationProvider = translationProvider;
        this.speechToTextProvider = speechToTextProvider;
        this.languageService = languageService;
        this.translationRepository = translationRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
    }

    public TranslationResponse translateText(long userId, TextTranslationRequest request) {
        Set<String> supported = languageService.translatableLanguageCodes();
        validateTarget(request.targetLanguage(), supported);
        if (!request.autoDetect()) {
            if (!supported.contains(request.sourceLanguage())) {
                throw ApiException.invalidField("sourceLanguage", "This source language is not supported.");
            }
            requireDifferent(request.sourceLanguage(), request.targetLanguage());
        }

        User user = loadUser(userId);
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

    /**
     * Speech → text → translation. The recording is only held in memory for the duration of this call;
     * only the recognised and translated text are saved.
     *
     * @param audio raw bytes of the uploaded file; validated here as 16 kHz mono PCM WAV
     */
    public TranslationResponse translateVoice(long userId, byte[] audio, String sourceLanguage, String targetLanguage) {
        if (sourceLanguage == null || !LANGUAGE_CODE.matcher(sourceLanguage).matches()) {
            throw ApiException.invalidField("sourceLanguage", "Choose the language you are speaking.");
        }
        if (targetLanguage == null || !LANGUAGE_CODE.matcher(targetLanguage).matches()) {
            throw ApiException.invalidField("targetLanguage", "Choose a target language.");
        }
        Set<String> voiceLanguages = languageService.voiceInputLanguageCodes();
        if (!voiceLanguages.contains(sourceLanguage)) {
            throw ApiException.invalidField("sourceLanguage",
                    "Voice input isn't available for " + LanguageService.nameOf(sourceLanguage) + ".");
        }
        validateTarget(targetLanguage, languageService.translatableLanguageCodes());
        requireDifferent(sourceLanguage, targetLanguage);
        WavAudio recording = WavAudio.parse(audio);

        User user = loadUser(userId);
        rateLimiter.consumeAiRequest(userId);

        String recognized = speechToTextProvider.transcribe(recording, sourceLanguage);
        if (recognized == null || recognized.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, NO_SPEECH_MESSAGE);
        }
        if (recognized.length() > TextTranslationRequest.MAX_TEXT_LENGTH) {
            recognized = recognized.substring(0, TextTranslationRequest.MAX_TEXT_LENGTH);
        }
        TranslationResult result = translationProvider.translate(recognized, sourceLanguage, targetLanguage);

        Translation saved = translationRepository.save(new Translation(user, recognized, result.translatedText(),
                sourceLanguage, targetLanguage, TranslationType.VOICE));
        return TranslationResponse.from(saved, false);
    }

    private User loadUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Your session is no longer valid. Please sign in again."));
    }

    private static void validateTarget(String targetLanguage, Set<String> supported) {
        if (!supported.contains(targetLanguage)) {
            throw ApiException.invalidField("targetLanguage", "This target language is not supported.");
        }
    }

    private static void requireDifferent(String sourceLanguage, String targetLanguage) {
        if (sourceLanguage.equals(targetLanguage)) {
            throw ApiException.invalidField("targetLanguage",
                    "Choose a target language that is different from the source language.");
        }
    }
}
