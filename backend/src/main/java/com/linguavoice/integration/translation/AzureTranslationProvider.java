package com.linguavoice.integration.translation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.CachedValue;
import com.linguavoice.integration.LanguageCodeMap;
import com.linguavoice.integration.ProviderErrors;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Azure AI Translator, REST API v3.
 * <ul>
 *   <li>{@code POST /translate?api-version=3.0&to=..[&from=..]} with body {@code [{"Text": "..."}]}</li>
 *   <li>{@code GET /languages?api-version=3.0&scope=translation} for the supported language list</li>
 * </ul>
 * Authentication: {@code Ocp-Apim-Subscription-Key}, plus {@code Ocp-Apim-Subscription-Region} for
 * regional (non-global) resources.
 */
public class AzureTranslationProvider implements TranslationProvider {

    public static final String DEFAULT_ENDPOINT = "https://api.cognitive.microsofttranslator.com";
    static final String DISPLAY_NAME = "Azure AI Translator";
    private static final Logger log = LoggerFactory.getLogger(AzureTranslationProvider.class);
    private static final LanguageCodeMap CODES = new LanguageCodeMap(Map.of("zh", "zh-Hans"));

    private final RestClient restClient;
    private final CachedValue<Set<String>> supportedLanguages;

    /**
     * @param restClientBuilder pre-configured builder (timeouts; tests bind a mock server to it)
     * @param region            Azure resource region, or blank for a global resource
     */
    public AzureTranslationProvider(RestClient.Builder restClientBuilder, String endpoint, String apiKey,
                                    String region, Clock clock) {
        this.restClient = restClientBuilder
                .baseUrl(endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint)
                .defaultHeaders(headers -> {
                    headers.set("Ocp-Apim-Subscription-Key", apiKey);
                    if (region != null && !region.isBlank()) {
                        headers.set("Ocp-Apim-Subscription-Region", region);
                    }
                })
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
        TranslateItem[] response;
        try {
            response = restClient.post()
                    .uri(uri -> {
                        uri.path("/translate")
                                .queryParam("api-version", "3.0")
                                .queryParam("to", CODES.toProvider(targetLanguage));
                        if (sourceLanguage != null) {
                            uri.queryParam("from", CODES.toProvider(sourceLanguage));
                        }
                        return uri.build();
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(Map.of("Text", text)))
                    .retrieve()
                    .body(TranslateItem[].class);
        } catch (RestClientException ex) {
            throw ProviderErrors.map(ex, DISPLAY_NAME, FEATURE, log);
        }

        if (response == null || response.length == 0
                || response[0].translations() == null || response[0].translations().isEmpty()
                || response[0].translations().get(0).text() == null) {
            log.error("{} returned an unexpected translate response", DISPLAY_NAME);
            throw AiProviderException.failed(FEATURE);
        }
        TranslateItem item = response[0];
        String detected = sourceLanguage == null && item.detectedLanguage() != null
                ? CODES.toApp(item.detectedLanguage().language())
                : null;
        return new TranslationResult(item.translations().get(0).text(), detected);
    }

    @Override
    public Set<String> supportedLanguages() {
        return supportedLanguages.get();
    }

    private Set<String> fetchSupportedLanguages() {
        LanguagesResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/languages")
                            .queryParam("api-version", "3.0")
                            .queryParam("scope", "translation")
                            .build())
                    .retrieve()
                    .body(LanguagesResponse.class);
        } catch (RestClientException ex) {
            throw ProviderErrors.map(ex, DISPLAY_NAME, FEATURE, log);
        }
        if (response == null || response.translation() == null || response.translation().isEmpty()) {
            log.error("{} returned an empty language list", DISPLAY_NAME);
            throw AiProviderException.failed(FEATURE);
        }
        return response.translation().keySet().stream().map(CODES::toApp).collect(Collectors.toUnmodifiableSet());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TranslateItem(DetectedLanguage detectedLanguage, List<Translation> translations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DetectedLanguage(String language, double score) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Translation(String text, String to) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LanguagesResponse(Map<String, Object> translation) {
    }
}
