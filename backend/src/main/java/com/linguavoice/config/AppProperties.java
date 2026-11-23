package com.linguavoice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
        @Valid @NotNull Jwt jwt) {

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
}
