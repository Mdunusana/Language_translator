package com.linguavoice.integration;

import org.slf4j.Logger;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Maps HTTP failures from any AI provider to user-safe {@link AiProviderException}s, logging the technical
 * detail for the operator. Response bodies are logged truncated and never returned to the client.
 */
public final class ProviderErrors {

    private static final int MAX_LOGGED_BODY = 300;

    private ProviderErrors() {
    }

    public static AiProviderException map(RestClientException ex, String provider, String feature, Logger log) {
        if (ex instanceof RestClientResponseException response) {
            HttpStatusCode status = response.getStatusCode();
            String body = truncate(response.getResponseBodyAsString());
            if (status.value() == 401 || status.value() == 403) {
                log.error("{} rejected the credentials (HTTP {}). Check the API key and region in .env. Body: {}",
                        provider, status.value(), body);
                return AiProviderException.misconfigured(feature);
            }
            if (status.value() == 429) {
                log.warn("{} is throttling requests (HTTP 429). Body: {}", provider, body);
                return AiProviderException.busy(feature);
            }
            log.error("{} returned HTTP {}. Body: {}", provider, status.value(), body);
            return AiProviderException.failed(feature);
        }
        if (ex instanceof ResourceAccessException) {
            log.error("{} could not be reached or timed out: {}", provider, ex.getMessage());
            return AiProviderException.failed(feature);
        }
        log.error("{} call failed: {}", provider, ex.getMessage(), ex);
        return AiProviderException.failed(feature);
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_LOGGED_BODY ? body : body.substring(0, MAX_LOGGED_BODY) + "…";
    }
}
