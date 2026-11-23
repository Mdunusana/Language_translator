package com.linguavoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the LinguaVoice REST API.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded so Spring Boot does not create a
 * throw-away in-memory user with a generated password. Real, database-backed users and JWT
 * authentication are introduced in Phase 2.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class LinguaVoiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinguaVoiceApplication.class, args);
    }
}
