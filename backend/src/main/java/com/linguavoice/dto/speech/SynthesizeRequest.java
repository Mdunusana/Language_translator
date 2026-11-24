package com.linguavoice.dto.speech;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/speech/synthesize}.
 *
 * @param voice optional voice id from {@code GET /api/speech/voices}; blank uses the language's default voice
 * @param speed speaking-rate multiplier 0.5–2.0; null means 1.0
 */
public record SynthesizeRequest(
        @NotBlank(message = "There is no text to read aloud.")
        @Size(max = 5000, message = "Text can be at most 5000 characters.")
        String text,

        @NotBlank(message = "Choose a language.")
        @Pattern(regexp = "^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$", message = "Unknown language.")
        String language,

        @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "Unknown voice.")
        String voice,

        @DecimalMin(value = "0.5", message = "Speed must be between 0.5× and 2×.")
        @DecimalMax(value = "2.0", message = "Speed must be between 0.5× and 2×.")
        Double speed) {

    public SynthesizeRequest {
        text = text == null ? null : text.strip();
        voice = voice == null ? "" : voice.trim();
    }

    public double speedOrDefault() {
        return speed == null ? 1.0 : speed;
    }
}
