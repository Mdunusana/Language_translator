package com.linguavoice.integration.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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

/** Checks the exact HTTP contract with Google Cloud Translation v2 against a mock server. */
class GoogleTranslationProviderTest {

    private static final String BASE = "https://google.test";

    private MockRestServiceServer server;
    private GoogleTranslationProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GoogleTranslationProvider(builder, BASE, "test-key", Clock.systemUTC());
    }

    @Test
    @DisplayName("sends the key in a header (not the URL), plain-text format and the source language")
    void translatesWithExplicitSource() {
        server.expect(requestTo(BASE + "/language/translate/v2"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-goog-api-key", "test-key"))
                .andExpect(content().json("{\"q\":\"Thank you\",\"target\":\"zu\",\"source\":\"en\",\"format\":\"text\"}"))
                .andRespond(withSuccess("{\"data\":{\"translations\":[{\"translatedText\":\"Ngiyabonga\"}]}}",
                        MediaType.APPLICATION_JSON));

        TranslationResult result = provider.translate("Thank you", "en", "zu");

        assertThat(result.translatedText()).isEqualTo("Ngiyabonga");
        assertThat(result.detectedSourceLanguage()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("auto-detection omits 'source' and maps zh-CN back to zh")
    void autoDetects() {
        server.expect(requestTo(BASE + "/language/translate/v2"))
                .andExpect(content().json("{\"q\":\"你好\",\"target\":\"en\",\"format\":\"text\"}", true))
                .andRespond(withSuccess("""
                        {"data":{"translations":[{"translatedText":"Hello","detectedSourceLanguage":"zh-CN"}]}}""",
                        MediaType.APPLICATION_JSON));

        TranslationResult result = provider.translate("你好", null, "en");

        assertThat(result.detectedSourceLanguage()).isEqualTo("zh");
    }

    @Test
    @DisplayName("reads the supported language list")
    void fetchesLanguages() {
        server.expect(requestTo(BASE + "/language/translate/v2/languages"))
                .andExpect(header("X-goog-api-key", "test-key"))
                .andRespond(withSuccess("""
                        {"data":{"languages":[{"language":"af"},{"language":"xh"},{"language":"zh-CN"},{"language":"en"}]}}""",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.supportedLanguages()).containsExactlyInAnyOrder("af", "xh", "zh", "en");
    }

    @Test
    @DisplayName("an invalid key (HTTP 403) becomes a 503 configuration error")
    void mapsAuthFailure() {
        server.expect(requestTo(BASE + "/language/translate/v2"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> provider.translate("Hi", "en", "af"))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("an empty language list is treated as a provider failure, not as 'no languages'")
    void rejectsEmptyLanguageList() {
        server.expect(requestTo(BASE + "/language/translate/v2/languages"))
                .andRespond(withSuccess("{\"data\":{\"languages\":[]}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.supportedLanguages())
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
