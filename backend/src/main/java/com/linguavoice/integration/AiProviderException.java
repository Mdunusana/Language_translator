package com.linguavoice.integration;

import com.linguavoice.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A failure involving an external AI service (translation, speech-to-text, text-to-speech).
 * The message is safe for end users; technical detail (status codes, provider responses) goes to the log.
 */
public class AiProviderException extends ApiException {

    public AiProviderException(HttpStatus status, String message) {
        super(status, message);
    }

    /** 503: the feature has no provider/credentials configured on this server. */
    public static AiProviderException notConfigured(String feature) {
        return new AiProviderException(HttpStatus.SERVICE_UNAVAILABLE,
                feature + " is not available: no AI provider is configured on the server.");
    }

    /** 503: the provider rejected our credentials, i.e. a server configuration problem. */
    public static AiProviderException misconfigured(String feature) {
        return new AiProviderException(HttpStatus.SERVICE_UNAVAILABLE,
                feature + " is temporarily unavailable because the AI provider is not configured correctly.");
    }

    /** 503: the provider is throttling us or over quota. */
    public static AiProviderException busy(String feature) {
        return new AiProviderException(HttpStatus.SERVICE_UNAVAILABLE,
                "The " + feature.toLowerCase() + " service is busy right now. Please try again in a moment.");
    }

    /** 502: the provider failed, timed out or returned something we could not use. */
    public static AiProviderException failed(String feature) {
        return new AiProviderException(HttpStatus.BAD_GATEWAY,
                "The " + feature.toLowerCase() + " service did not respond correctly. Please try again.");
    }
}
