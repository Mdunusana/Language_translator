package com.linguavoice.dto;

import java.time.Instant;

/**
 * Public health information used by the frontend to confirm it can reach the API.
 * Deliberately contains no hostnames, credentials or stack details.
 */
public record HealthResponse(
        String status,
        String application,
        String version,
        String database,
        Instant timestamp) {
}
