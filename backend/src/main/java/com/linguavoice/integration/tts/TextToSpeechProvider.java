package com.linguavoice.integration.tts;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over a speech-synthesis service, chosen with {@code TTS_PROVIDER}.
 * Voices come from the provider's live voice list, so a language is only offered for speech output
 * when the provider really has a voice for it.
 */
public interface TextToSpeechProvider {

    String FEATURE = "Text-to-speech";

    String displayName();

    boolean isConfigured();

    /** Voices grouped by LinguaVoice language code, best default first. Cached by implementations. */
    Map<String, List<Voice>> voicesByLanguage();

    /**
     * @param text  plain text (escaped by the implementation where needed)
     * @param voice one of the voices returned by {@link #voicesByLanguage()}
     * @param speed speaking rate multiplier, 0.5–2.0 (1.0 = normal)
     * @return MP3 audio
     * @throws com.linguavoice.integration.AiProviderException on provider failure
     */
    byte[] synthesize(String text, Voice voice, double speed);

    /** Largest input this provider accepts in one call, in UTF-8 bytes. */
    default int maxInputBytes() {
        return Integer.MAX_VALUE;
    }
}
