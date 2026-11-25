package com.linguavoice.config;

import com.linguavoice.repository.UserRepository;
import com.linguavoice.security.TokenRevocationValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Self-issued, HMAC-SHA256-signed access tokens. The API both issues them (on login/registration)
 * and validates them on every request through Spring Security's bearer-token filter.
 */
@Configuration
public class JwtConfig {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Bean
    SecretKey jwtSigningKey(AppProperties properties) {
        return new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    /**
     * Rejects tokens with a bad signature, a different algorithm, a wrong issuer, an expiry in the past,
     * a deleted account, or an issue time before the user's last password change.
     */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AppProperties properties, UserRepository userRepository) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()),
                new TokenRevocationValidator(userRepository)));
        return decoder;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
