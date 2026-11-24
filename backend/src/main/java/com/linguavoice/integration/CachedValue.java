package com.linguavoice.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Lazily loads a value and keeps it for {@code ttl}. A failed load is not cached, so the next call retries.
 * Used for provider capability lists, which change rarely but should not be fetched on every request.
 */
public final class CachedValue<T> {

    private final Supplier<T> loader;
    private final Duration ttl;
    private final Clock clock;
    private T value;
    private Instant loadedAt;

    public CachedValue(Supplier<T> loader, Duration ttl, Clock clock) {
        this.loader = loader;
        this.ttl = ttl;
        this.clock = clock;
    }

    public synchronized T get() {
        Instant now = clock.instant();
        if (value == null || loadedAt.plus(ttl).isBefore(now)) {
            value = loader.get();
            loadedAt = now;
        }
        return value;
    }
}
