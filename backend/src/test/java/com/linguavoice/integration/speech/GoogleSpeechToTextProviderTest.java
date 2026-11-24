package com.linguavoice.integration.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleSpeechToTextProviderTest {

    private static final String BASE = "https://speech.test";

    private MockRestServiceServer server;
    private GoogleSpeechToTextProvider provider;
    private final WavAudio audio = WavAudio.parse(TestWav.valid(1));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GoogleSpeechToTextProvider(builder, BASE, "google-key");
    }

    @Test
    @DisplayName("sends LINEAR16 config and base64 audio with the key in a header; joins result segments")
    void recognises() {
        server.expect(requestTo(BASE + "/v1/speech:recognize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-goog-api-key", "google-key"))
                .andExpect(jsonPath("$.config.encoding").value("LINEAR16"))
                .andExpect(jsonPath("$.config.sampleRateHertz").value(16000))
                .andExpect(jsonPath("$.config.languageCode").value("af-ZA"))
                .andExpect(jsonPath("$.config.model").doesNotExist())
                .andExpect(jsonPath("$.audio.content").value(Base64.getEncoder().encodeToString(audio.bytes())))
                .andRespond(withSuccess("""
                        {"results":[{"alternatives":[{"transcript":"Goeie môre","confidence":0.92}]},
                                    {"alternatives":[{"transcript":" hoe gaan dit","confidence":0.88}]}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.transcribe(audio, "af")).isEqualTo("Goeie môre hoe gaan dit");
        server.verify();
    }

    @Test
    @DisplayName("isiXhosa uses the latest_long model, the only v1 model that supports xh-ZA")
    void xhosaUsesLatestModel() {
        server.expect(requestTo(BASE + "/v1/speech:recognize"))
                .andExpect(jsonPath("$.config.languageCode").value("xh-ZA"))
                .andExpect(jsonPath("$.config.model").value("latest_long"))
                .andRespond(withSuccess("{\"results\":[{\"alternatives\":[{\"transcript\":\"Molo\"}]}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.transcribe(audio, "xh")).isEqualTo("Molo");
        assertThat(provider.supportedLanguages()).contains("xh", "zu", "af");
    }

    @Test
    @DisplayName("an empty response means no speech was heard")
    void noSpeech() {
        server.expect(requestTo(BASE + "/v1/speech:recognize"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(provider.transcribe(audio, "en")).isEmpty();
    }
}
