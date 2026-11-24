package com.linguavoice.integration.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linguavoice.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Upload safety: only real 16 kHz mono 16-bit PCM WAV files between 0.3 s and 60 s are accepted. */
class WavAudioTest {

    @Test
    @DisplayName("accepts the browser's format and measures the duration")
    void acceptsValidRecording() {
        WavAudio audio = WavAudio.parse(TestWav.valid(2.5));
        assertThat(audio.duration()).isEqualTo(Duration.ofMillis(2500));
    }

    @Test
    @DisplayName("rejects files that are not WAV, judged by their bytes rather than their name or content type")
    void rejectsNonWav() {
        assertInvalid("%PDF-1.7 pretend this is a recording, padded to be long enough....".getBytes(StandardCharsets.US_ASCII),
                "must be a WAV");
        assertInvalid(new byte[0], "must be a WAV");
        assertInvalid(null, "must be a WAV");
    }

    @Test
    @DisplayName("rejects other sample rates, stereo and 8-bit audio")
    void rejectsWrongFormat() {
        assertInvalid(TestWav.build(1, 44_100, 1, 16), "16 kHz");
        assertInvalid(TestWav.build(1, 16_000, 2, 16), "16 kHz");
        assertInvalid(TestWav.build(1, 16_000, 1, 8), "16 kHz");
    }

    @Test
    @DisplayName("rejects recordings that are too short or longer than 60 seconds")
    void rejectsBadDurations() {
        assertInvalid(TestWav.valid(0.1), "too short");
        assertInvalid(TestWav.valid(61), "at most 60 seconds");
        assertThat(WavAudio.parse(TestWav.valid(60)).duration()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("rejects a truncated header")
    void rejectsTruncatedHeader() {
        byte[] truncated = Arrays.copyOf(TestWav.valid(1), 30);
        assertInvalid(truncated, "must be a WAV");
    }

    private static void assertInvalid(byte[] data, String messagePart) {
        assertThatThrownBy(() -> WavAudio.parse(data))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getFieldErrors()).containsKey("audio");
                    assertThat(ex.getFieldErrors().get("audio")).contains(messagePart);
                });
    }
}
