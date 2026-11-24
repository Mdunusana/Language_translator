package com.linguavoice.integration.speech;

import com.linguavoice.exception.ApiException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * A validated voice recording: RIFF/WAVE, 16-bit PCM, mono, 16 kHz, between 0.3 and 60 seconds.
 *
 * <p>The browser converts every recording to exactly this format before upload, so the server accepts
 * nothing else. The file's bytes are inspected (magic numbers and the {@code fmt } chunk), not its
 * declared content type, so a renamed file of another type is rejected. Recordings are held in memory only
 * and never written to disk or the database.
 */
public final class WavAudio {

    public static final int SAMPLE_RATE = 16_000;
    public static final int BITS_PER_SAMPLE = 16;
    public static final Duration MAX_DURATION = Duration.ofSeconds(60);
    public static final Duration MIN_DURATION = Duration.ofMillis(300);

    private static final int PCM_FORMAT = 1;
    private static final int HEADER_MIN_LENGTH = 44;

    private final byte[] bytes;
    private final Duration duration;

    private WavAudio(byte[] bytes, Duration duration) {
        this.bytes = bytes;
        this.duration = duration;
    }

    /** The complete WAV file, ready to send to a speech provider. */
    public byte[] bytes() {
        return bytes;
    }

    public Duration duration() {
        return duration;
    }

    /**
     * @throws ApiException 400 on the {@code audio} field when the bytes are not an acceptable recording
     */
    public static WavAudio parse(byte[] data) {
        if (data == null || data.length < HEADER_MIN_LENGTH
                || !ascii(data, 0, "RIFF") || !ascii(data, 8, "WAVE")) {
            throw invalid("The recording must be a WAV audio file.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        boolean formatChecked = false;
        long dataLength = -1;
        int offset = 12;
        while (offset + 8 <= data.length) {
            String chunkId = new String(data, offset, 4, StandardCharsets.US_ASCII);
            long chunkSize = Integer.toUnsignedLong(buffer.getInt(offset + 4));
            int body = offset + 8;
            if (chunkId.equals("fmt ")) {
                if (chunkSize < 16 || body + 16 > data.length) {
                    throw invalid("The recording's WAV header is damaged.");
                }
                int audioFormat = Short.toUnsignedInt(buffer.getShort(body));
                int channels = Short.toUnsignedInt(buffer.getShort(body + 2));
                int sampleRate = buffer.getInt(body + 4);
                int bitsPerSample = Short.toUnsignedInt(buffer.getShort(body + 14));
                if (audioFormat != PCM_FORMAT || channels != 1 || sampleRate != SAMPLE_RATE
                        || bitsPerSample != BITS_PER_SAMPLE) {
                    throw invalid("The recording must be 16 kHz, 16-bit, mono PCM audio.");
                }
                formatChecked = true;
            } else if (chunkId.equals("data")) {
                dataLength = Math.min(chunkSize, data.length - body);
                break;
            }
            offset = (int) Math.min((long) body + chunkSize + (chunkSize & 1), Integer.MAX_VALUE); // chunks are word-aligned
        }
        if (!formatChecked || dataLength < 0) {
            throw invalid("The recording's WAV header is damaged.");
        }

        long bytesPerSecond = (long) SAMPLE_RATE * (BITS_PER_SAMPLE / 8);
        Duration duration = Duration.ofMillis(dataLength * 1000 / bytesPerSecond);
        if (duration.compareTo(MIN_DURATION) < 0) {
            throw invalid("The recording is too short. Hold the button and speak for at least a second.");
        }
        if (duration.compareTo(MAX_DURATION.plusMillis(500)) > 0) {
            throw invalid("Recordings can be at most 60 seconds long.");
        }
        return new WavAudio(data, duration);
    }

    private static boolean ascii(byte[] data, int offset, String expected) {
        return new String(data, offset, 4, StandardCharsets.US_ASCII).equals(expected);
    }

    private static ApiException invalid(String message) {
        return ApiException.invalidField("audio", message);
    }
}
