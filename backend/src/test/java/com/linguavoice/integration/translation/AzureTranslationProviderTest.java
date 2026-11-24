package com.linguavoice.integration.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.linguavoice.integration.AiProviderException;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Checks the exact HTTP contract with Azure Translator v3 against a mock server (no network, no cost). */
class AzureTranslationProviderTest {

    private static final String BASE = "https://azure.test";

    private MockRestServiceServer server;
    private AzureTranslationProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new AzureTranslationProvider(builder, BASE, "test-key", "southafricanorth", Clock.systemUTC());
    }

    @Test
    @DisplayName("sends key, region, target and source, and returns the translation")
    void translatesWithExplicitSource() {
        server.expect(requestTo(BASE + "/translate?api-version=3.0&to=af&from=en"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Ocp-Apim-Subscription-Key", "test-key"))
                .andExpect(header("Ocp-Apim-Subscription-Region", "southafricanorth"))
                .andExpect(content().json("[{\"Text\":\"Good morning\"}]"))
                .andRespond(withSuccess("[{\"translations\":[{\"text\":\"Goeie môre\",\"to\":\"af\"}]}]",
                        MediaType.APPLICATION_JSON));

        TranslationResult result = provider.translate("Good morning", "en", "af");

        assertThat(result.translatedText()).isEqualTo("Goeie môre");
        assertThat(result.detectedSourceLanguage()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("omits 'from' for auto-detection and maps Azure's zh-Hans back to zh")
    void autoDetectsAndMapsCodes() {
        server.expect(requestTo(BASE + "/translate?api-version=3.0&to=en"))
                .andRespond(withSuccess("""
                        [{"detectedLanguage":{"language":"zh-Hans","score":0.98},
                          "translations":[{"text":"Hello","to":"en"}]}]""", MediaType.APPLICATION_JSON));

        TranslationResult result = provider.translate("你好", null, "en");

        assertThat(result.translatedText()).isEqualTo("Hello");
        assertThat(result.detectedSourceLanguage()).isEqualTo("zh");
    }

    @Test
    @DisplayName("maps the LinguaVoice target 'zh' to Azure's zh-Hans")
    void mapsTargetCode() {
        server.expect(requestTo(BASE + "/translate?api-version=3.0&to=zh-Hans&from=en"))
                .andRespond(withSuccess("[{\"translations\":[{\"text\":\"你好\",\"to\":\"zh-Hans\"}]}]",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.translate("Hello", "en", "zh").translatedText()).isEqualTo("你好");
    }

    @Test
    @DisplayName("reads supported languages and caches them for later calls")
    void fetchesAndCachesLanguages() {
        server.expect(requestTo(BASE + "/languages?api-version=3.0&scope=translation"))
                .andRespond(withSuccess("""
                        {"translation":{"af":{"name":"Afrikaans"},"zu":{"name":"Zulu"},
                         "zh-Hans":{"name":"Chinese Simplified"},"en":{"name":"English"}}}""",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.supportedLanguages()).containsExactlyInAnyOrder("af", "zu", "zh", "en");
        assertThat(provider.supportedLanguages()).hasSize(4); // second call served from cache
        server.verify(); // exactly one HTTP request was made
    }

    @Test
    @DisplayName("a rejected key becomes a 503 'not configured correctly' error")
    void mapsAuthFailure() {
        server.expect(requestTo(BASE + "/translate?api-version=3.0&to=af&from=en"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":{\"code\":401000}}"));

        assertThatThrownBy(() -> provider.translate("Hi", "en", "af"))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getMessage()).contains("not configured correctly").doesNotContain("401000");
                });
    }

    @Test
    @DisplayName("throttling becomes 503 busy, and server errors or bad bodies become 502")
    void mapsOtherFailures() {
        server.expect(requestTo(BASE + "/translate?api-version=3.0&to=af&from=en"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> provider.translate("Hi", "en", "af"))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).contains("busy"));

        server.reset();
        server.expect(requestTo(BASE + "/translate?api-version=3.0&to=af&from=en"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> provider.translate("Hi", "en", "af"))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));

        server.reset();
        server.expect(requestTo(BASE + "/translate?api-version=3.0&to=af&from=en"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> provider.translate("Hi", "en", "af"))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    @DisplayName("a global resource (no region) sends no region header")
    void omitsRegionHeaderForGlobalResource() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer globalServer = MockRestServiceServer.bindTo(builder).build();
        AzureTranslationProvider global = new AzureTranslationProvider(builder, BASE, "k", "", Clock.systemUTC());
        globalServer.expect(requestTo(BASE + "/translate?api-version=3.0&to=af&from=en"))
                .andExpect(headerDoesNotExist("Ocp-Apim-Subscription-Region"))
                .andRespond(withSuccess("[{\"translations\":[{\"text\":\"Hallo\"}]}]", MediaType.APPLICATION_JSON));

        assertThat(global.translate("Hello", "en", "af").translatedText()).isEqualTo("Hallo");
    }
}
