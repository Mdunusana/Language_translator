package com.linguavoice.config;

import com.linguavoice.integration.ProviderHttp;
import com.linguavoice.integration.tts.AzureTextToSpeechProvider;
import com.linguavoice.integration.tts.GoogleTextToSpeechProvider;
import com.linguavoice.integration.tts.NotConfiguredTextToSpeechProvider;
import com.linguavoice.integration.tts.TextToSpeechProvider;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Chooses the {@link TextToSpeechProvider} from TTS_PROVIDER / TTS_API_KEY / TTS_REGION. */
@Configuration
public class TextToSpeechProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeechProviderConfig.class);

    @Bean
    TextToSpeechProvider textToSpeechProvider(AppProperties properties, RestClient.Builder restClientBuilder,
                                              Clock clock) {
        AppProperties.TextToSpeech settings = properties.ai().textToSpeech();
        String provider = settings.provider() == null ? "" : settings.provider().trim().toLowerCase(Locale.ROOT);

        if (provider.isEmpty() || provider.equals("none")) {
            return disabled("TTS_PROVIDER is not set");
        }
        if (isBlank(settings.apiKey())) {
            return disabled("TTS_PROVIDER=" + provider + " but TTS_API_KEY is empty");
        }
        if (provider.equals("azure") && isBlank(settings.region()) && isBlank(settings.endpoint())) {
            return disabled("TTS_PROVIDER=azure needs TTS_REGION (the Speech resource's region)");
        }

        RestClient.Builder builder = restClientBuilder.clone()
                .requestFactory(ProviderHttp.requestFactory(settings.timeoutSeconds()));
        TextToSpeechProvider selected = switch (provider) {
            case "azure" -> new AzureTextToSpeechProvider(builder, settings.endpoint(), settings.apiKey(),
                    settings.region(), clock);
            case "google" -> new GoogleTextToSpeechProvider(builder, settings.endpoint(), settings.apiKey(), clock);
            default -> throw new IllegalStateException("Unsupported TTS_PROVIDER: " + provider);
        };
        log.info("Text-to-speech provider: {}", selected.displayName());
        return selected;
    }

    private static TextToSpeechProvider disabled(String reason) {
        log.warn("Server-side text-to-speech is DISABLED ({}). The browser's own voices are used as a fallback.", reason);
        return new NotConfiguredTextToSpeechProvider(reason);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
