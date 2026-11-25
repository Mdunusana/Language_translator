package com.linguavoice.exception;

import org.springframework.http.HttpStatus;

/** 429 Too Many Requests; the handler adds a {@code Retry-After} header. */
public class RateLimitExceededException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        this(retryAfterSeconds,
                "You're sending requests too quickly. Please wait " + retryAfterSeconds + " seconds and try again.");
    }

    public RateLimitExceededException(long retryAfterSeconds, String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
