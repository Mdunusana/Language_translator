package com.linguavoice.config;

import com.linguavoice.integration.translation.AzureTranslationProvider;
import com.linguavoice.integration.translation.GoogleTranslationProvider;
import com.linguavoice.integration.translation.NotConfiguredTranslationProvider;
import com.linguavoice.integration.translation.TranslationProvider;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Chooses the {@link TranslationProvider} from TRANSLATION_PROVIDER / TRANSLATION_API_KEY. */
@Configuration
public class TranslationProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(TranslationProviderConfig.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    TranslationProvider translationProvider(AppProperties properties, RestClient.Builder restClientBuilder,
                                            Clock clock) {
        AppProperties.Translation settings = properties.ai().translation();
        String provider = settings.provider() == null ? "" : settings.provider().trim().toLowerCase(Locale.ROOT);

        if (provider.isEmpty() || provider.equals("none")) {
            return disabled("TRANSLATION_PROVIDER is not set");
        }
        if (settings.apiKey() == null || settings.apiKey().isBlank()) {
            return disabled("TRANSLATION_PROVIDER=" + provider + " but TRANSLATION_API_KEY is empty");
        }

        RestClient.Builder builder = restClientBuilder.clone().requestFactory(requestFactory(settings.timeoutSeconds()));
        TranslationProvider selected = switch (provider) {
            case "azure" -> new AzureTranslationProvider(builder, settings.endpoint(), settings.apiKey(),
                    settings.region(), clock);
            case "google" -> new GoogleTranslationProvider(builder, settings.endpoint(), settings.apiKey(), clock);
            default -> throw new IllegalStateException("Unsupported TRANSLATION_PROVIDER: " + provider);
        };
        log.info("Text translation provider: {}", selected.displayName());
        return selected;
    }

    private static TranslationProvider disabled(String reason) {
        log.warn("Text translation is DISABLED ({}). Translation requests will return HTTP 503. See .env.example.",
                reason);
        return new NotConfiguredTranslationProvider(reason);
    }

    private static JdkClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return factory;
    }
}
