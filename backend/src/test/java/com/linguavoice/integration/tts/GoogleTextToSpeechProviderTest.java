package com.linguavoice.integration.tts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleTextToSpeechProviderTest {

    private static final String BASE = "https://gtts.test";

    private MockRestServiceServer server;
    private GoogleTextToSpeechProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GoogleTextToSpeechProvider(builder, BASE, "google-key", Clock.systemUTC());
    }

    @Test
    @DisplayName("keeps Standard/WaveNet/Neural2 voices, maps cmn-CN to Mandarin and names voices readably")
    void listsVoices() {
        server.expect(requestTo(BASE + "/v1/voices"))
                .andExpect(header("X-goog-api-key", "google-key"))
                .andRespond(withSuccess("""
                        {"voices":[
                          {"languageCodes":["af-ZA"],"name":"af-ZA-Standard-A","ssmlGender":"FEMALE"},
                          {"languageCodes":["cmn-CN"],"name":"cmn-CN-Wavenet-B","ssmlGender":"MALE"},
                          {"languageCodes":["en-US"],"name":"en-US-Chirp3-HD-Achernar","ssmlGender":"FEMALE"},
                          {"languageCodes":["en-US"],"name":"en-US-Neural2-C","ssmlGender":"FEMALE"}]}""",
                        MediaType.APPLICATION_JSON));

        Map<String, List<Voice>> voices = provider.voicesByLanguage();

        assertThat(voices).containsOnlyKeys("af", "zh", "en");
        assertThat(voices.get("en")).extracting(Voice::id).containsExactly("en-US-Neural2-C"); // Chirp skipped
        assertThat(voices.get("zh").get(0)).isEqualTo(new Voice("cmn-CN-Wavenet-B", "WaveNet B", "cmn-CN", "Male"));
    }

    @Test
    @DisplayName("sends text, voice and speakingRate, and decodes the base64 MP3")
    void synthesizes() {
        byte[] mp3 = {(byte) 0xFF, (byte) 0xF3, 0x44};
        server.expect(requestTo(BASE + "/v1/text:synthesize"))
                .andExpect(jsonPath("$.input.text").value("Goeie môre"))
                .andExpect(jsonPath("$.voice.languageCode").value("af-ZA"))
                .andExpect(jsonPath("$.voice.name").value("af-ZA-Standard-A"))
                .andExpect(jsonPath("$.audioConfig.audioEncoding").value("MP3"))
                .andExpect(jsonPath("$.audioConfig.speakingRate").value(0.75))
                .andRespond(withSuccess("{\"audioContent\":\"" + Base64.getEncoder().encodeToString(mp3) + "\"}",
                        MediaType.APPLICATION_JSON));

        byte[] audio = provider.synthesize("Goeie môre",
                new Voice("af-ZA-Standard-A", "Standard A", "af-ZA", "Female"), 0.75);

        assertThat(audio).isEqualTo(mp3);
        assertThat(provider.maxInputBytes()).isEqualTo(5000);
    }
}
