package com.linguavoice.service;

import com.linguavoice.dto.speech.SynthesizeRequest;
import com.linguavoice.dto.speech.VoicesResponse;
import com.linguavoice.dto.speech.VoicesResponse.VoiceResponse;
import com.linguavoice.exception.ApiException;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.tts.TextToSpeechProvider;
import com.linguavoice.integration.tts.Voice;
import com.linguavoice.security.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Server-side text-to-speech. The generated audio is streamed to the caller and never stored. */
@Service
public class SpeechService {

    private static final Logger log = LoggerFactory.getLogger(SpeechService.class);

    private final TextToSpeechProvider provider;
    private final RateLimiter rateLimiter;

    public SpeechService(TextToSpeechProvider provider, RateLimiter rateLimiter) {
        this.provider = provider;
        this.rateLimiter = rateLimiter;
    }

    /** Result of a synthesis: MP3 bytes and the voice that was used. */
    public record SynthesizedSpeech(byte[] audio, Voice voice) {
    }

    public VoicesResponse voices(String language) {
        requireCatalogLanguage(language, "language");
        if (!provider.isConfigured()) {
            return new VoicesResponse(language, false, null, List.of());
        }
        List<Voice> voices = provider.voicesByLanguage().getOrDefault(language, List.of());
        return new VoicesResponse(language, !voices.isEmpty(), provider.displayName(),
                voices.stream().map(VoiceResponse::from).toList());
    }

    /**
     * Languages with at least one server voice. Used by the language list, which must keep working even if
     * the voice list cannot be fetched right now: in that case no server voices are advertised.
     */
    public Set<String> languagesWithVoices() {
        if (!provider.isConfigured()) {
            return Set.of();
        }
        try {
            return provider.voicesByLanguage().keySet();
        } catch (AiProviderException ex) {
            log.warn("Text-to-speech voices are unavailable right now; advertising no server voices: {}", ex.getMessage());
            return Set.of();
        }
    }

    public String providerName() {
        return provider.isConfigured() ? provider.displayName() : null;
    }

    public SynthesizedSpeech synthesize(long userId, SynthesizeRequest request) {
        if (!provider.isConfigured()) {
            throw AiProviderException.notConfigured(TextToSpeechProvider.FEATURE);
        }
        String language = request.language();
        requireCatalogLanguage(language, "language");

        Map<String, List<Voice>> byLanguage = provider.voicesByLanguage();
        List<Voice> voices = byLanguage.getOrDefault(language, List.of());
        if (voices.isEmpty()) {
            throw ApiException.invalidField("language",
                    "No voice is available for " + LanguageService.nameOf(language) + ".");
        }
        Voice voice = request.voice().isEmpty()
                ? voices.get(0)
                : voices.stream().filter(v -> v.id().equals(request.voice())).findFirst()
                        .orElseThrow(() -> ApiException.invalidField("voice",
                                "This voice isn't available for " + LanguageService.nameOf(language) + "."));
        if (request.text().getBytes(StandardCharsets.UTF_8).length > provider.maxInputBytes()) {
            throw ApiException.invalidField("text", "This text is too long to read aloud in one go.");
        }

        rateLimiter.consumeAiRequest(userId);
        return new SynthesizedSpeech(provider.synthesize(request.text(), voice, request.speedOrDefault()), voice);
    }

    private static void requireCatalogLanguage(String language, String field) {
        boolean known = LanguageCatalog.LANGUAGES.stream().anyMatch(l -> l.code().equals(language));
        if (!known) {
            throw ApiException.invalidField(field, "Unknown language.");
        }
    }
}
