package com.linguavoice.integration.tts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.CachedValue;
import com.linguavoice.integration.ProviderErrors;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Azure AI Speech text-to-speech, REST API.
 * <ul>
 *   <li>{@code GET /cognitiveservices/voices/list} for the voice list (neural voices only)</li>
 *   <li>{@code POST /cognitiveservices/v1} with an SSML body; MP3 output</li>
 * </ul>
 */
public class AzureTextToSpeechProvider implements TextToSpeechProvider {

    static final String DISPLAY_NAME = "Azure AI Speech";
    static final String OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3";
    private static final Logger log = LoggerFactory.getLogger(AzureTextToSpeechProvider.class);

    private final RestClient restClient;
    private final CachedValue<Map<String, List<Voice>>> voices;

    /** @param endpoint optional override; blank derives {@code https://{region}.tts.speech.microsoft.com} */
    public AzureTextToSpeechProvider(RestClient.Builder restClientBuilder, String endpoint, String apiKey,
                                     String region, Clock clock) {
        String baseUrl = endpoint == null || endpoint.isBlank()
                ? "https://" + region + ".tts.speech.microsoft.com"
                : endpoint;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Ocp-Apim-Subscription-Key", apiKey)
                .defaultHeader("User-Agent", "LinguaVoice") // required by the Azure TTS REST API
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
    public Map<String, List<Voice>> voicesByLanguage() {
        return voices.get();
    }

    @Override
    public byte[] synthesize(String text, Voice voice, double speed) {
        byte[] audio;
        try {
            audio = restClient.post()
                    .uri("/cognitiveservices/v1")
                    .contentType(MediaType.parseMediaType("application/ssml+xml"))
                    .header("X-Microsoft-OutputFormat", OUTPUT_FORMAT)
                    .body(ssml(text, voice, speed).getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException ex) {
            throw ProviderErrors.map(ex, DISPLAY_NAME, FEATURE, log);
        }
        if (audio == null || audio.length == 0) {
            log.error("{} returned no audio", DISPLAY_NAME);
            throw AiProviderException.failed(FEATURE);
        }
        return audio;
    }

    /**
     * Builds the SSML document. The text is XML-escaped, so user content can never add or change SSML
     * elements; the voice id comes from the provider's own list, never directly from the request.
     */
    static String ssml(String text, Voice voice, double speed) {
        long percent = Math.round((speed - 1.0) * 100);
        String rate = (percent >= 0 ? "+" : "") + percent + "%";
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='" + escape(voice.locale()) + "'>"
                + "<voice name='" + escape(voice.id()) + "'>"
                + "<prosody rate='" + rate + "'>" + escape(text) + "</prosody>"
                + "</voice></speak>";
    }

    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> {
                    // Drop control characters that are illegal in XML 1.0 (keep tab, newline, carriage return).
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private Map<String, List<Voice>> fetchVoices() {
        VoiceEntry[] entries;
        try {
            entries = restClient.get().uri("/cognitiveservices/voices/list").retrieve().body(VoiceEntry[].class);
        } catch (RestClientException ex) {
            throw ProviderErrors.map(ex, DISPLAY_NAME, FEATURE, log);
        }
        if (entries == null || entries.length == 0) {
            log.error("{} returned an empty voice list", DISPLAY_NAME);
            throw AiProviderException.failed(FEATURE);
        }
        List<Voice> neural = Arrays.stream(entries)
                .filter(entry -> entry.shortName() != null && entry.locale() != null)
                .filter(entry -> entry.voiceType() == null || entry.voiceType().toLowerCase(Locale.ROOT).contains("neural"))
                .map(entry -> new Voice(entry.shortName(),
                        entry.displayName() == null ? entry.shortName() : entry.displayName(),
                        entry.locale(), entry.gender() == null ? "Neutral" : entry.gender()))
                .toList();
        return VoiceCatalog.group(neural);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VoiceEntry(
            @JsonProperty("ShortName") String shortName,
            @JsonProperty("DisplayName") String displayName,
            @JsonProperty("Locale") String locale,
            @JsonProperty("Gender") String gender,
            @JsonProperty("VoiceType") String voiceType) {
    }
}
