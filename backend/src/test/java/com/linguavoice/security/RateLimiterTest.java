package com.linguavoice.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.linguavoice.config.AppProperties;
import com.linguavoice.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private MutableClock clock;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        AppProperties properties = mock(AppProperties.class);
        when(properties.rateLimit()).thenReturn(new AppProperties.RateLimit(3));
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        rateLimiter = new RateLimiter(properties, clock);
    }

    @Test
    @DisplayName("allows the configured number of requests per minute, then returns 429 with a wait time")
    void limitsBurst() {
        for (int i = 0; i < 3; i++) {
            rateLimiter.consumeAiRequest(1L);
        }
        assertThatThrownBy(() -> rateLimiter.consumeAiRequest(1L))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 20L));
    }

    @Test
    @DisplayName("tokens refill over time")
    void refills() {
        for (int i = 0; i < 3; i++) {
            rateLimiter.consumeAiRequest(1L);
        }
        clock.advance(Duration.ofSeconds(21)); // 3 per minute → one token every 20 s
        assertThatCode(() -> rateLimiter.consumeAiRequest(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("each user has an independent budget")
    void bucketsArePerUser() {
        for (int i = 0; i < 3; i++) {
            rateLimiter.consumeAiRequest(1L);
        }
        assertThatCode(() -> rateLimiter.consumeAiRequest(2L)).doesNotThrowAnyException();
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
