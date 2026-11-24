package com.linguavoice.security;

import com.linguavoice.config.AppProperties;
import com.linguavoice.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory token-bucket rate limiter for the paid AI endpoints. Each user gets a bucket of
 * {@code aiRequestsPerMinute} tokens that refills continuously; an empty bucket results in HTTP 429.
 *
 * <p>Single-instance only: with several backend replicas this would move to Redis (noted in ARCHITECTURE.md).
 */
@Component
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(10);
    private static final int EVICTION_THRESHOLD = 10_000;

    private final int capacity;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(AppProperties properties, Clock clock) {
        this.capacity = properties.rateLimit().aiRequestsPerMinute();
        this.clock = clock;
    }

    /** Takes one token for {@code userId}, or throws {@link RateLimitExceededException}. */
    public void consumeAiRequest(long userId) {
        long now = clock.millis();
        if (buckets.size() > EVICTION_THRESHOLD) {
            evictIdle(now);
        }
        long waitMillis = buckets.computeIfAbsent("ai:" + userId, key -> new Bucket(capacity, now)).tryConsume(now);
        if (waitMillis > 0) {
            throw new RateLimitExceededException(Math.max(1, (waitMillis + 999) / 1000));
        }
    }

    private void evictIdle(long now) {
        buckets.entrySet().removeIf(entry -> now - entry.getValue().lastUsed() > IDLE_EVICTION.toMillis());
    }

    private static final class Bucket {
        private final int capacity;
        private final double tokensPerMilli;
        private double tokens;
        private long lastRefill;
        private long lastUsed;

        Bucket(int capacity, long now) {
            this.capacity = capacity;
            this.tokensPerMilli = (double) capacity / WINDOW.toMillis();
            this.tokens = capacity;
            this.lastRefill = now;
            this.lastUsed = now;
        }

        /** @return 0 if a token was taken, otherwise milliseconds until one is available */
        synchronized long tryConsume(long now) {
            tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerMilli);
            lastRefill = now;
            lastUsed = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return (long) Math.ceil((1 - tokens) / tokensPerMilli);
        }

        synchronized long lastUsed() {
            return lastUsed;
        }
    }
}
