package com.linguavoice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe view of the {@code app.*} configuration block. Values come from
 * {@code application.yml}, which in turn reads environment variables / the local {@code .env} file.
 * Startup fails fast with a clear message if a required value is missing or invalid.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String name,
        @NotBlank String version,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Ai ai,
        @Valid @NotNull RateLimit rateLimit) {

    /** Origins (scheme + host + port) that the browser frontend is served from. */
    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    /**
     * @param secret            HMAC-SHA256 signing key; at least 32 characters (256 bits). Set JWT_SECRET.
     * @param expirationMinutes access-token lifetime
     * @param issuer            value of the {@code iss} claim, checked on every request
     */
    public record Jwt(
            @NotBlank(message = "JWT_SECRET must be set (see .env.example)")
            @Size(min = 32, message = "JWT_SECRET must be at least 32 characters")
            String secret,
            @Min(5) @Max(1440) int expirationMinutes,
            @NotBlank String issuer) {
    }

    /** External AI services. Text-to-speech is added in Phase 5. */
    public record Ai(@Valid @NotNull Translation translation, @Valid @NotNull SpeechToText speechToText) {
    }

    /**
     * @param provider       {@code azure}, {@code google}, or {@code none}/blank to disable server-side voice input
     * @param apiKey         Azure Speech key or Google Cloud API key (STT_API_KEY)
     * @param region         Azure Speech resource region (e.g. {@code southafricanorth}); required for Azure
     * @param endpoint       optional base-URL override; blank uses the provider's public endpoint
     * @param timeoutSeconds upper bound for one recognition call
     */
    public record SpeechToText(
            @Pattern(regexp = "(?i)^(|none|azure|google)$", message = "STT_PROVIDER must be azure, google or none")
            String provider,
            String apiKey,
            String region,
            String endpoint,
            @Min(1) @Max(120) int timeoutSeconds) {
    }

    /**
     * @param provider       {@code azure}, {@code google}, or {@code none}/blank to disable translation
     * @param apiKey         provider key; read from TRANSLATION_API_KEY and never sent to the browser
     * @param region         Azure resource region (e.g. {@code southafricanorth}); unused by Google
     * @param endpoint       optional base-URL override; blank uses the provider's public endpoint
     * @param timeoutSeconds upper bound for one provider call
     */
    public record Translation(
            @Pattern(regexp = "(?i)^(|none|azure|google)$",
                    message = "TRANSLATION_PROVIDER must be azure, google or none")
            String provider,
            String apiKey,
            String region,
            String endpoint,
            @Min(1) @Max(60) int timeoutSeconds) {
    }

    /** Per-user budget shared by all paid AI endpoints (translation now, speech in later phases). */
    public record RateLimit(@Min(1) @Max(10_000) int aiRequestsPerMinute) {
    }
}
