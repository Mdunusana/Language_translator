package com.linguavoice.config;

import com.linguavoice.integration.ProviderHttp;
import com.linguavoice.integration.speech.AzureSpeechToTextProvider;
import com.linguavoice.integration.speech.GoogleSpeechToTextProvider;
import com.linguavoice.integration.speech.NotConfiguredSpeechToTextProvider;
import com.linguavoice.integration.speech.SpeechToTextProvider;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Chooses the {@link SpeechToTextProvider} from STT_PROVIDER / STT_API_KEY / STT_REGION. */
@Configuration
public class SpeechProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(SpeechProviderConfig.class);

    @Bean
    SpeechToTextProvider speechToTextProvider(AppProperties properties, RestClient.Builder restClientBuilder) {
        AppProperties.SpeechToText settings = properties.ai().speechToText();
        String provider = settings.provider() == null ? "" : settings.provider().trim().toLowerCase(Locale.ROOT);

        if (provider.isEmpty() || provider.equals("none")) {
            return disabled("STT_PROVIDER is not set");
        }
        if (isBlank(settings.apiKey())) {
            return disabled("STT_PROVIDER=" + provider + " but STT_API_KEY is empty");
        }
        if (provider.equals("azure") && isBlank(settings.region()) && isBlank(settings.endpoint())) {
            return disabled("STT_PROVIDER=azure needs STT_REGION (the Speech resource's region)");
        }

        RestClient.Builder builder = restClientBuilder.clone()
                .requestFactory(ProviderHttp.requestFactory(settings.timeoutSeconds()));
        SpeechToTextProvider selected = switch (provider) {
            case "azure" -> new AzureSpeechToTextProvider(builder, settings.endpoint(), settings.apiKey(),
                    settings.region());
            case "google" -> new GoogleSpeechToTextProvider(builder, settings.endpoint(), settings.apiKey());
            default -> throw new IllegalStateException("Unsupported STT_PROVIDER: " + provider);
        };
        log.info("Speech-to-text provider: {}", selected.displayName());
        return selected;
    }

    private static SpeechToTextProvider disabled(String reason) {
        log.warn("Server-side voice input is DISABLED ({}). Voice requests return HTTP 503; the browser's own "
                + "speech recognition is offered as a fallback where available.", reason);
        return new NotConfiguredSpeechToTextProvider(reason);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
