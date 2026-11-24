package com.linguavoice.integration.tts;

import com.linguavoice.integration.AiProviderException;
import java.util.List;
import java.util.Map;

/** Used when TTS_PROVIDER is not set; the frontend falls back to the browser's own voices. */
public class NotConfiguredTextToSpeechProvider implements TextToSpeechProvider {

    private final String reason;

    public NotConfiguredTextToSpeechProvider(String reason) {
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
    public Map<String, List<Voice>> voicesByLanguage() {
        return Map.of();
    }

    @Override
    public byte[] synthesize(String text, Voice voice, double speed) {
        throw AiProviderException.notConfigured(FEATURE);
    }
}
