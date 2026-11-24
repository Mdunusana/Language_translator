package com.linguavoice.integration.tts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.CachedValue;
import com.linguavoice.integration.ProviderErrors;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Google Cloud Text-to-Speech v1.
 * <ul>
 *   <li>{@code GET /v1/voices}: Standard, WaveNet and Neural2 voices are offered (these all support speakingRate)</li>
 *   <li>{@code POST /v1/text:synthesize} → base64 MP3 in {@code audioContent}</li>
 * </ul>
 */
public class GoogleTextToSpeechProvider implements TextToSpeechProvider {

    public static final String DEFAULT_ENDPOINT = "https://texttospeech.googleapis.com";
    static final String DISPLAY_NAME = "Google Cloud Text-to-Speech";
    /** Google limits the input to 5000 bytes per request. */
    static final int MAX_INPUT_BYTES = 5000;
    private static final Logger log = LoggerFactory.getLogger(GoogleTextToSpeechProvider.class);
    private static final Pattern SUPPORTED_NAME = Pattern.compile("^[a-z]{2,3}-[A-Z]{2}-(Standard|Wavenet|Neural2)-([A-Z])$");

    private final RestClient restClient;
    private final CachedValue<Map<String, List<Voice>>> voices;

    public GoogleTextToSpeechProvider(RestClient.Builder restClientBuilder, String endpoint, String apiKey, Clock clock) {
        this.restClient = restClientBuilder
                .baseUrl(endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint)
                .defaultHeader("X-goog-api-key", apiKey)
                .build();
        this.voices = new CachedValue<>(this::fetchVoices, Duration.ofHours(24), clock);
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public int maxInputBytes() {
        return MAX_INPUT_BYTES;
    }

    @Override
    public Map<String, List<Voice>> voicesByLanguage() {
        return voices.get();
    }

    @Override
    public byte[] synthesize(String text, Voice voice, double speed) {
        Map<String, Object> body = Map.of(
                "input", Map.of("text", text),
                "voice", Map.of("languageCode", voice.locale(), "name", voice.id()),
                "audioConfig", Map.of("audioEncoding", "MP3", "speakingRate", speed));
        SynthesizeResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/text:synthesize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(SynthesizeResponse.class);
        } catch (RestClientException ex) {
            throw ProviderErrors.map(ex, DISPLAY_NAME, FEATURE, log);
        }
        if (response == null || response.audioContent() == null || response.audioContent().isEmpty()) {
            log.error("{} returned no audio", DISPLAY_NAME);
            throw AiProviderException.failed(FEATURE);
        }
        try {
            return Base64.getDecoder().decode(response.audioContent());
        } catch (IllegalArgumentException ex) {
            log.error("{} returned audio that is not valid base64", DISPLAY_NAME);
            throw AiProviderException.failed(FEATURE);
        }
    }

    private Map<String, List<Voice>> fetchVoices() {
        VoicesResponse response;
        try {
            response = restClient.get().uri("/v1/voices").retrieve().body(VoicesResponse.class);
        } catch (RestClientException ex) {
            throw ProviderErrors.map(ex, DISPLAY_NAME, FEATURE, log);
        }
        if (response == null || response.voices() == null || response.voices().isEmpty()) {
            log.error("{} returned an empty voice list", DISPLAY_NAME);
            throw AiProviderException.failed(FEATURE);
        }
        List<Voice> usable = new ArrayList<>();
        for (VoiceEntry entry : response.voices()) {
            if (entry.name() == null || entry.languageCodes() == null || entry.languageCodes().isEmpty()) {
                continue;
            }
            Matcher matcher = SUPPORTED_NAME.matcher(entry.name());
            if (!matcher.matches()) {
                continue;
            }
            String type = matcher.group(1).equals("Wavenet") ? "WaveNet" : matcher.group(1);
            usable.add(new Voice(entry.name(), type + " " + matcher.group(2),
                    entry.languageCodes().get(0), gender(entry.ssmlGender())));
        }
        return VoiceCatalog.group(usable);
    }

    private static String gender(String ssmlGender) {
        if (ssmlGender == null) {
            return "Neutral";
        }
        String lower = ssmlGender.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VoicesResponse(List<VoiceEntry> voices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VoiceEntry(List<String> languageCodes, String name, String ssmlGender) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SynthesizeResponse(String audioContent) {
    }
}
