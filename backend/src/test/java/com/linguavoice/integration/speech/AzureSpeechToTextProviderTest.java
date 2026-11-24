package com.linguavoice.integration.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.linguavoice.integration.AiProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AzureSpeechToTextProviderTest {

    private static final String BASE = "https://stt.test";
    private static final String PATH = "/speech/recognition/conversation/cognitiveservices/v1";

    private MockRestServiceServer server;
    private AzureSpeechToTextProvider provider;
    private final WavAudio audio = WavAudio.parse(TestWav.valid(1.5));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new AzureSpeechToTextProvider(builder, BASE, "speech-key", "southafricanorth");
    }

    @Test
    @DisplayName("posts the raw WAV with the PCM content type, key and locale, and returns DisplayText")
    void recognises() {
        server.expect(requestTo(BASE + PATH + "?language=zu-ZA&format=simple"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Ocp-Apim-Subscription-Key", "speech-key"))
                .andExpect(header("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000"))
                .andExpect(content().bytes(audio.bytes()))
                .andRespond(withSuccess("{\"RecognitionStatus\":\"Success\",\"DisplayText\":\"Sawubona.\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.transcribe(audio, "zu")).isEqualTo("Sawubona.");
        server.verify();
    }

    @Test
    @DisplayName("NoMatch and silence timeouts mean 'heard nothing', not an error")
    void noSpeechIsEmpty() {
        server.expect(requestTo(BASE + PATH + "?language=en-US&format=simple"))
                .andRespond(withSuccess("{\"RecognitionStatus\":\"InitialSilenceTimeout\"}", MediaType.APPLICATION_JSON));
        assertThat(provider.transcribe(audio, "en")).isEmpty();
    }

    @Test
    @DisplayName("an Error status or HTTP failure becomes a provider error")
    void failures() {
        server.expect(requestTo(BASE + PATH + "?language=en-US&format=simple"))
                .andRespond(withSuccess("{\"RecognitionStatus\":\"Error\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> provider.transcribe(audio, "en"))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));

        server.reset();
        server.expect(requestTo(BASE + PATH + "?language=en-US&format=simple"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> provider.transcribe(audio, "en"))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("does not claim isiXhosa, which Azure speech-to-text does not support")
    void languageSupport() {
        assertThat(provider.supportedLanguages()).contains("en", "af", "zu").doesNotContain("xh");
    }

    @Test
    @DisplayName("derives the regional endpoint when no override is given")
    void regionalEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer regional = MockRestServiceServer.bindTo(builder).build();
        AzureSpeechToTextProvider p = new AzureSpeechToTextProvider(builder, "", "k", "southafricanorth");
        regional.expect(requestTo("https://southafricanorth.stt.speech.microsoft.com" + PATH + "?language=af-ZA&format=simple"))
                .andRespond(withSuccess("{\"RecognitionStatus\":\"Success\",\"DisplayText\":\"Hallo\"}",
                        MediaType.APPLICATION_JSON));
        assertThat(p.transcribe(audio, "af")).isEqualTo("Hallo");
    }
}
