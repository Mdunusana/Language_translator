package com.linguavoice.integration.speech;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.ProviderErrors;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Azure AI Speech, REST API for short audio (up to 60 s):
 * {@code POST https://{region}.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1
 * ?language=xx-XX&format=simple} with a 16 kHz mono PCM WAV body.
 */
public class AzureSpeechToTextProvider implements SpeechToTextProvider {

    static final String DISPLAY_NAME = "Azure AI Speech";
    static final String CONTENT_TYPE = "audio/wav; codecs=audio/pcm; samplerate=16000";
    private static final Logger log = LoggerFactory.getLogger(AzureSpeechToTextProvider.class);

    /**
     * LinguaVoice code → Azure speech-to-text locale. Only languages listed in Azure's speech-to-text
     * language table are included; isiXhosa (xh-ZA) is not supported by Azure and is therefore absent.
     */
    static final Map<String, String> LOCALES = Map.ofEntries(
            Map.entry("en", "en-US"),
            Map.entry("af", "af-ZA"),
            Map.entry("zu", "zu-ZA"),
            Map.entry("es", "es-ES"),
            Map.entry("fr", "fr-FR"),
            Map.entry("pt", "pt-BR"),
            Map.entry("de", "de-DE"),
            Map.entry("zh", "zh-CN"),
            Map.entry("ar", "ar-SA"),
            Map.entry("ja", "ja-JP"),
            Map.entry("sw", "sw-KE"));

    private final RestClient restClient;

    /** @param endpoint optional override; blank derives {@code https://{region}.stt.speech.microsoft.com} */
    public AzureSpeechToTextProvider(RestClient.Builder restClientBuilder, String endpoint, String apiKey,
                                     String region) {
        String baseUrl = endpoint == null || endpoint.isBlank()
                ? "https://" + region + ".stt.speech.microsoft.com"
                : endpoint;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Ocp-Apim-Subscription-Key", apiKey)
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
        String locale = LOCALES.get(language);
        if (locale == null) {
            throw new IllegalArgumentException("Unsupported speech language: " + language);
        }
        RecognitionResponse response;
        try {
            response = restClient.post()
                    .uri(uri -> uri.path("/speech/recognition/conversation/cognitiveservices/v1")
                            .queryParam("language", locale)
                            .queryParam("format", "simple")
                            .build())
                    // Azure's documented content type has an unquoted '/' in a parameter, which Spring's strict
                    // MediaType parser rejects. Send it verbatim and stream the bytes ourselves, bypassing the
                    // message converters that would try to parse it.
                    .header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(output -> output.write(audio.bytes()))
                    .retrieve()
                    .body(RecognitionResponse.class);
        } catch (RestClientException ex) {
            throw ProviderErrors.map(ex, DISPLAY_NAME, FEATURE, log);
        }
        if (response == null || response.status() == null) {
            log.error("{} returned an empty recognition response", DISPLAY_NAME);
            throw AiProviderException.failed(FEATURE);
        }
        return switch (response.status()) {
            case "Success" -> response.displayText() == null ? "" : response.displayText().trim();
            // The provider worked but heard nothing usable: the caller turns this into a friendly message.
            case "NoMatch", "InitialSilenceTimeout", "BabbleTimeout" -> "";
            default -> {
                log.error("{} recognition failed with status {}", DISPLAY_NAME, response.status());
                throw AiProviderException.failed(FEATURE);
            }
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecognitionResponse(
            @JsonProperty("RecognitionStatus") String status,
            @JsonProperty("DisplayText") String displayText) {
    }
}
