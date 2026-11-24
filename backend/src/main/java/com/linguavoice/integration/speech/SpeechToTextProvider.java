package com.linguavoice.integration.speech;

import java.util.Set;

/**
 * Abstraction over a speech-recognition service. Implementations are chosen with {@code STT_PROVIDER}.
 * Language codes crossing this interface are LinguaVoice codes; each adapter maps them to its own locales.
 */
public interface SpeechToTextProvider {

    String FEATURE = "Voice input";

    String displayName();

    boolean isConfigured();

    /**
     * @param audio    a validated 16 kHz mono PCM recording
     * @param language LinguaVoice code of the spoken language (speech providers need it up front)
     * @return the recognised text; empty when the provider heard no speech
     * @throws com.linguavoice.integration.AiProviderException on provider failure
     */
    String transcribe(WavAudio audio, String language);

    /** LinguaVoice codes this provider can recognise, as documented by the provider. */
    Set<String> supportedLanguages();
}
