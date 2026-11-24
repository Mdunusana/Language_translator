package com.linguavoice.integration.speech;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.linguavoice.integration.ProviderErrors;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Google Cloud Speech-to-Text v1, synchronous recognition (up to 60 s):
 * {@code POST /v1/speech:recognize} with LINEAR16 audio, base64-encoded in the JSON body.
 * The key goes in the {@code X-goog-api-key} header, never the URL.
 */
public class GoogleSpeechToTextProvider implements SpeechToTextProvider {

    public static final String DEFAULT_ENDPOINT = "https://speech.googleapis.com";
    static final String DISPLAY_NAME = "Google Cloud Speech-to-Text";
    private static final Logger log = LoggerFactory.getLogger(GoogleSpeechToTextProvider.class);

    /** LinguaVoice code → Google v1 locale and model, per Google's v1 supported-languages table. */
    record LocaleModel(String locale, String model) {
    }

    static final Map<String, LocaleModel> LOCALES = Map.ofEntries(
            Map.entry("en", new LocaleModel("en-US", null)),
            Map.entry("af", new LocaleModel("af-ZA", null)),
            Map.entry("zu", new LocaleModel("zu-ZA", null)),
            // isiXhosa is only available on the "latest" models in v1.
            Map.entry("xh", new LocaleModel("xh-ZA", "latest_long")),
            Map.entry("es", new LocaleModel("es-ES", null)),
            Map.entry("fr", new LocaleModel("fr-FR", null)),
            Map.entry("pt", new LocaleModel("pt-BR", null)),
            Map.entry("de", new LocaleModel("de-DE", null)),
            Map.entry("zh", new LocaleModel("cmn-Hans-CN", null)),
            Map.entry("ar", new LocaleModel("ar-SA", null)),
            Map.entry("ja", new LocaleModel("ja-JP", null)),
            Map.entry("sw", new LocaleModel("sw-KE", null)));

    private final RestClient restClient;

    public GoogleSpeechToTextProvider(RestClient.Builder restClientBuilder, String endpoint, String apiKey) {
        this.restClient = restClientBuilder
                .baseUrl(endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint)
                .defaultHeader("X-goog-api-key", apiKey)
                .build();
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
    public Set<String> supportedLanguages() {
        return LOCALES.keySet();
    }

    @Override
    public String transcribe(WavAudio audio, String language) {
        LocaleModel localeModel = LOCALES.get(language);
        if (localeModel == null) {
            throw new IllegalArgumentException("Unsupported speech language: " + language);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("encoding", "LINEAR16");
        config.put("sampleRateHertz", WavAudio.SAMPLE_RATE);
        config.put("languageCode", localeModel.locale());
        config.put("enableAutomaticPunctuation", true);
        if (localeModel.model() != null) {
            config.put("model", localeModel.model());
        }
        Map<String, Object> body = Map.of(
                "config", config,
                "audio", Map.of("content", Base64.getEncoder().encodeToString(audio.bytes())));

        RecognizeResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/speech:recognize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(RecognizeResponse.class);
        } catch (RestClientException ex) {
            throw ProviderErrors.map(ex, DISPLAY_NAME, FEATURE, log);
        }
        // Google answers {} (no results) when it hears no speech.
        if (response == null || response.results() == null) {
            return "";
        }
        return response.results().stream()
                .map(Result::alternatives)
                .filter(alternatives -> alternatives != null && !alternatives.isEmpty())
                .map(alternatives -> alternatives.get(0).transcript())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" "));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecognizeResponse(List<Result> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(List<Alternative> alternatives) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Alternative(String transcript, double confidence) {
    }
}
