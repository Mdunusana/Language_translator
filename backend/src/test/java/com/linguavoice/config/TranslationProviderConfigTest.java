package com.linguavoice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.linguavoice.integration.translation.AzureTranslationProvider;
import com.linguavoice.integration.translation.GoogleTranslationProvider;
import com.linguavoice.integration.translation.NotConfiguredTranslationProvider;
import com.linguavoice.integration.translation.TranslationProvider;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Provider selection from configuration. No HTTP calls are made: providers fetch lazily. */
class TranslationProviderConfigTest {

    private final TranslationProviderConfig config = new TranslationProviderConfig();

    private TranslationProvider select(String provider, String apiKey) {
        AppProperties properties = new AppProperties("LinguaVoice", "test",
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Jwt("x".repeat(32), 60, "linguavoice"),
                new AppProperties.Ai(new AppProperties.Translation(provider, apiKey, "southafricanorth", "", 10)),
                new AppProperties.RateLimit(30));
        return config.translationProvider(properties, RestClient.builder(), Clock.systemUTC());
    }

    @Test
    @DisplayName("blank or 'none' disables translation")
    void disabledByDefault() {
        assertThat(select("", "key")).isInstanceOf(NotConfiguredTranslationProvider.class);
        assertThat(select("none", "key")).isInstanceOf(NotConfiguredTranslationProvider.class);
    }

    @Test
    @DisplayName("a provider without an API key is disabled, never half-configured")
    void missingKeyDisables() {
        TranslationProvider provider = select("azure", " ");
        assertThat(provider.isConfigured()).isFalse();
        assertThat(((NotConfiguredTranslationProvider) provider).reason()).contains("TRANSLATION_API_KEY");
    }

    @Test
    @DisplayName("selects Azure or Google by name (case-insensitive)")
    void selectsProvider() {
        assertThat(select("Azure", "key")).isInstanceOf(AzureTranslationProvider.class);
        assertThat(select("google", "key")).isInstanceOf(GoogleTranslationProvider.class);
    }
}
