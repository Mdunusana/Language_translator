package com.linguavoice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.linguavoice.integration.speech.AzureSpeechToTextProvider;
import com.linguavoice.integration.speech.GoogleSpeechToTextProvider;
import com.linguavoice.integration.speech.NotConfiguredSpeechToTextProvider;
import com.linguavoice.integration.speech.SpeechToTextProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SpeechProviderConfigTest {

    private final SpeechProviderConfig config = new SpeechProviderConfig();

    private SpeechToTextProvider select(String provider, String key, String region) {
        AppProperties properties = new AppProperties("LinguaVoice", "test",
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Jwt("x".repeat(32), 60, "linguavoice"),
                new AppProperties.Ai(new AppProperties.Translation("none", "", "", "", 10),
                        new AppProperties.SpeechToText(provider, key, region, "", 30),
                        new AppProperties.TextToSpeech("none", "", "", "", 30)),
                new AppProperties.RateLimit(30));
        return config.speechToTextProvider(properties, RestClient.builder());
    }

    @Test
    @DisplayName("disabled when unset, without a key, or (for Azure) without a region")
    void disabledCases() {
        assertThat(select("", "k", "r")).isInstanceOf(NotConfiguredSpeechToTextProvider.class);
        assertThat(select("google", "", "")).isInstanceOf(NotConfiguredSpeechToTextProvider.class);
        SpeechToTextProvider azureNoRegion = select("azure", "k", "");
        assertThat(azureNoRegion).isInstanceOf(NotConfiguredSpeechToTextProvider.class);
        assertThat(((NotConfiguredSpeechToTextProvider) azureNoRegion).reason()).contains("STT_REGION");
    }

    @Test
    @DisplayName("selects Azure or Google")
    void selects() {
        assertThat(select("azure", "k", "southafricanorth")).isInstanceOf(AzureSpeechToTextProvider.class);
        assertThat(select("Google", "k", "")).isInstanceOf(GoogleSpeechToTextProvider.class);
    }
}
