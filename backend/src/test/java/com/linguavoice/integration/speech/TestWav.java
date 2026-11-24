package com.linguavoice.integration.speech;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Builds WAV files for tests: a 440 Hz tone with a configurable format and length. */
public final class TestWav {

    private TestWav() {
    }

    /** A valid recording in the format the browser sends: 16 kHz, 16-bit, mono. */
    public static byte[] valid(double seconds) {
        return build(seconds, 16_000, 1, 16);
    }

    public static byte[] build(double seconds, int sampleRate, int channels, int bitsPerSample) {
        int bytesPerSample = bitsPerSample / 8;
        int frames = (int) Math.round(seconds * sampleRate);
        int dataLength = frames * channels * bytesPerSample;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataLength)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1)                                  // PCM
                .putShort((short) channels)
                .putInt(sampleRate)
                .putInt(sampleRate * channels * bytesPerSample)       // byte rate
                .putShort((short) (channels * bytesPerSample))        // block align
                .putShort((short) bitsPerSample)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataLength);
        for (int i = 0; i < frames; i++) {
            short sample = (short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 8000);
            for (int c = 0; c < channels; c++) {
                if (bytesPerSample == 2) {
                    buffer.putShort(sample);
                } else {
                    buffer.put((byte) (sample >> 8));
                }
            }
        }
        return buffer.array();
    }
}
