package com.linguavoice.integration.translation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.CachedValue;
import com.linguavoice.integration.LanguageCodeMap;
import com.linguavoice.integration.ProviderErrors;
import java.time.Clock;
import java.time.Duration;
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
 * Google Cloud Translation, Basic edition (REST v2).
 * <ul>
 *   <li>{@code POST /language/translate/v2} with body {@code {q, target, source?, format: "text"}}</li>
 *   <li>{@code GET /language/translate/v2/languages} for the supported language list</li>
 * </ul>
 * The API key is sent in the {@code X-goog-api-key} header rather than the URL, so it never appears in
 * request logs.
 */
public class GoogleTranslationProvider implements TranslationProvider {

    public static final String DEFAULT_ENDPOINT = "https://translation.googleapis.com";
    static final String DISPLAY_NAME = "Google Cloud Translation";
    private static final Logger log = LoggerFactory.getLogger(GoogleTranslationProvider.class);
    private static final LanguageCodeMap CODES = new LanguageCodeMap(Map.of("zh", "zh-CN"));

    private final RestClient restClient;
    private final CachedValue<Set<String>> supportedLanguages;

    public GoogleTranslationProvider(RestClient.Builder restClientBuilder, String endpoint, String apiKey, Clock clock) {
        this.restClient = restClientBuilder
                .baseUrl(endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint)
                .defaultHeader("X-goog-api-key", apiKey)
                .build();
        this.supportedLanguages = new CachedValue<>(this::fetchSupportedLanguages, Duration.ofHours(24), clock);
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
    public TranslationResult translate(String text, String sourceLanguage, String targetLanguage) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("q", text);
        body.put("target", CODES.toProvider(targetLanguage));
        body.put("format", "text"); // plain text: no HTML escaping of the result
        if (sourceLanguage != null) {
            body.put("source", CODES.toProvider(sourceLanguage));
        }

        TranslateResponse response;
        try {
            response = restClient.post()
                    .uri("/language/translate/v2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TranslateResponse.class);
        } catch (RestClientException ex) {
            throw ProviderErrors.map(ex, DISPLAY_NAME, FEATURE, log);
        }

        if (response == null || response.data() == null || response.data().translations() == null
                || response.data().translations().isEmpty()
                || response.data().translations().get(0).translatedText() == null) {
            log.error("{} returned an unexpected translate response", DISPLAY_NAME);
            throw AiProviderException.failed(FEATURE);
        }
        Translation translation = response.data().translations().get(0);
        String detected = sourceLanguage == null ? CODES.toApp(translation.detectedSourceLanguage()) : null;
        return new TranslationResult(translation.translatedText(), detected);
    }

    @Override
    public Set<String> supportedLanguages() {
        return supportedLanguages.get();
    }

    private Set<String> fetchSupportedLanguages() {
        LanguagesResponse response;
        try {
            response = restClient.get()
                    .uri("/language/translate/v2/languages")
                    .retrieve()
                    .body(LanguagesResponse.class);
        } catch (RestClientException ex) {
            throw ProviderErrors.map(ex, DISPLAY_NAME, FEATURE, log);
        }
        if (response == null || response.data() == null || response.data().languages() == null
                || response.data().languages().isEmpty()) {
            log.error("{} returned an empty language list", DISPLAY_NAME);
            throw AiProviderException.failed(FEATURE);
        }
        return response.data().languages().stream()
                .map(Language::language)
                .filter(Objects::nonNull)
                .map(CODES::toApp)
                .collect(Collectors.toUnmodifiableSet());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TranslateResponse(TranslateData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TranslateData(List<Translation> translations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Translation(String translatedText, String detectedSourceLanguage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LanguagesResponse(LanguagesData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LanguagesData(List<Language> languages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Language(String language) {
    }
}
