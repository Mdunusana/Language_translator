package com.linguavoice.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base class for expected, client-facing errors. The message is safe to show to the user;
 * {@link GlobalExceptionHandler} turns it into an {@code ApiError} with the given status.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    public ApiException(HttpStatus status, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    /** 400 with a single field error, e.g. "passwords do not match". */
    public static ApiException invalidField(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "One or more fields are invalid.", Map.of(field, message));
    }

    /** 409 with a field error, e.g. email already registered. */
    public static ApiException conflict(String field, String message) {
        return new ApiException(HttpStatus.CONFLICT, message, Map.of(field, message));
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }
}
