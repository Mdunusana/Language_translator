package com.linguavoice.dto.user;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * {@code PUT /api/users/preferences}. Languages must be in the LinguaVoice catalog (checked in the service);
 * the source may also be {@code auto}.
 */
public record UpdatePreferencesRequest(
        @NotBlank(message = "Choose a default source language.")
        @Pattern(regexp = "^([a-z]{2,3}|auto)$", message = "Unknown language.")
        String preferredSourceLanguage,

        @NotBlank(message = "Choose a default target language.")
        @Pattern(regexp = "^[a-z]{2,3}$", message = "Unknown language.")
        String preferredTargetLanguage,

        @NotNull(message = "Choose a speaking speed.")
        @DecimalMin(value = "0.50", message = "Speed must be between 0.5× and 2×.")
        @DecimalMax(value = "2.00", message = "Speed must be between 0.5× and 2×.")
        @Digits(integer = 1, fraction = 2, message = "Speed can have at most two decimals.")
        BigDecimal speechSpeed) {
}
