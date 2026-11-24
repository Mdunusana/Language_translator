package com.linguavoice.integration.speech;

import com.linguavoice.integration.AiProviderException;
import java.util.Set;

/** Used when STT_PROVIDER is not set. The frontend then offers the browser's own recognition as a fallback. */
public class NotConfiguredSpeechToTextProvider implements SpeechToTextProvider {

    private final String reason;

    public NotConfiguredSpeechToTextProvider(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String displayName() {
        return "Not configured";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String transcribe(WavAudio audio, String language) {
        throw AiProviderException.notConfigured(FEATURE);
    }

    @Override
    public Set<String> supportedLanguages() {
        return Set.of();
    }
}
