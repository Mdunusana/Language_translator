package com.linguavoice.dto.speech;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.linguavoice.integration.tts.Voice;
import java.util.List;

/**
 * {@code GET /api/speech/voices?language=xx}.
 *
 * @param available false when the server has no voice for this language (the browser's voices are then used)
 * @param voices    best default first
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoicesResponse(String language, boolean available, String provider, List<VoiceResponse> voices) {

    public record VoiceResponse(String id, String name, String locale, String gender) {

        public static VoiceResponse from(Voice voice) {
            return new VoiceResponse(voice.id(), voice.name(), voice.locale(), voice.gender());
        }
    }
}
