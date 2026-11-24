package com.linguavoice.integration.tts;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AzureTextToSpeechProviderTest {

    private static final String BASE = "https://tts.test";
    private static final Voice THANDO = new Voice("zu-ZA-ThandoNeural", "Thando", "zu-ZA", "Female");

    private MockRestServiceServer server;
    private AzureTextToSpeechProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new AzureTextToSpeechProvider(builder, BASE, "speech-key", "southafricanorth", Clock.systemUTC());
    }

    @Test
    @DisplayName("groups neural voices by LinguaVoice language with the preferred locale first; skips unknown languages")
    void listsVoices() {
        server.expect(requestTo(BASE + "/cognitiveservices/voices/list"))
                .andExpect(header("Ocp-Apim-Subscription-Key", "speech-key"))
                .andRespond(withSuccess("""
                        [{"ShortName":"en-AU-NatashaNeural","DisplayName":"Natasha","Locale":"en-AU","Gender":"Female","VoiceType":"Neural"},
                         {"ShortName":"en-US-JennyNeural","DisplayName":"Jenny","Locale":"en-US","Gender":"Female","VoiceType":"Neural"},
                         {"ShortName":"zu-ZA-ThandoNeural","DisplayName":"Thando","Locale":"zu-ZA","Gender":"Female","VoiceType":"Neural"},
                         {"ShortName":"zh-CN-XiaoxiaoNeural","DisplayName":"Xiaoxiao","Locale":"zh-CN","Gender":"Female","VoiceType":"Neural"},
                         {"ShortName":"zh-HK-HiuMaanNeural","DisplayName":"HiuMaan","Locale":"zh-HK","Gender":"Female","VoiceType":"Neural"},
                         {"ShortName":"ko-KR-SunHiNeural","DisplayName":"SunHi","Locale":"ko-KR","Gender":"Female","VoiceType":"Neural"},
                         {"ShortName":"en-US-OldStandard","DisplayName":"Old","Locale":"en-US","Gender":"Male","VoiceType":"Standard"}]""",
                        MediaType.APPLICATION_JSON));

        Map<String, List<Voice>> voices = provider.voicesByLanguage();

        assertThat(voices).containsOnlyKeys("en", "zu", "zh");
        assertThat(voices.get("en")).extracting(Voice::id).containsExactly("en-US-JennyNeural", "en-AU-NatashaNeural");
        assertThat(voices.get("zh")).extracting(Voice::id).containsExactly("zh-CN-XiaoxiaoNeural"); // no Cantonese
    }

    @Test
    @DisplayName("posts SSML with voice and prosody rate, asks for MP3, returns the audio bytes")
    void synthesizes() {
        byte[] mp3 = {(byte) 0xFF, (byte) 0xFB, 0x10, 0x00};
        server.expect(requestTo(BASE + "/cognitiveservices/v1"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/ssml+xml"))
                .andExpect(header("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3"))
                .andExpect(header("User-Agent", "LinguaVoice"))
                .andExpect(content().string(
                        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zu-ZA'>"
                                + "<voice name='zu-ZA-ThandoNeural'><prosody rate='+25%'>Sawubona</prosody></voice></speak>"))
                .andRespond(withSuccess(mp3, MediaType.parseMediaType("audio/mpeg")));

        assertThat(provider.synthesize("Sawubona", THANDO, 1.25)).isEqualTo(mp3);
    }

    @Test
    @DisplayName("escapes text so it cannot inject SSML elements")
    void escapesText() {
        String ssml = AzureTextToSpeechProvider.ssml("</prosody></voice><voice name='evil'>Hi & bye\u0007", THANDO, 0.5);

        assertThat(ssml)
                .contains("&lt;/prosody&gt;&lt;/voice&gt;&lt;voice name=&apos;evil&apos;&gt;Hi &amp; bye")
                .contains("rate='-50%'")
                .doesNotContain("\u0007")
                .containsOnlyOnce("<voice ");
    }

    @Test
    @DisplayName("provider failures map to user-safe errors")
    void failures() {
        server.expect(requestTo(BASE + "/cognitiveservices/v1")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> provider.synthesize("Hi", THANDO, 1.0))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        server.reset();
        server.expect(requestTo(BASE + "/cognitiveservices/v1"))
                .andRespond(withSuccess(new byte[0], MediaType.parseMediaType("audio/mpeg")));
        assertThatThrownBy(() -> provider.synthesize("Hi", THANDO, 1.0))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
