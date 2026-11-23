package com.linguavoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Uniform error body returned by every failing API call.
 *
 * @param fieldErrors per-field validation messages; omitted from the JSON when empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, Map.of());
    }

    public static ApiError withFieldErrors(HttpStatus status, String message, String path,
                                           Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
    }
}
