package com.linguavoice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linguavoice.exception.RateLimitExceededException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    private RateLimiterTest.MutableClock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new RateLimiterTest.MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        limiter = new LoginAttemptLimiter(5, 15, clock);
    }

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email);
        }
    }

    @Test
    @DisplayName("locks an address after 5 failures, with a wait time in the message and header value")
    void locksAfterMaxFailures() {
        fail("ada@example.com", 4);
        assertThatCode(() -> limiter.checkAllowed("ada@example.com")).doesNotThrowAnyException();

        fail("ada@example.com", 1);
        assertThatThrownBy(() -> limiter.checkAllowed("ada@example.com"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
                    assertThat(ex.getRetryAfterSeconds()).isBetween(14 * 60L, 15 * 60L);
                    assertThat(ex.getMessage()).isEqualTo("Too many failed sign-in attempts. Try again in 15 minutes.");
                });
    }

    @Test
    @DisplayName("the lock expires after the lockout period")
    void lockExpires() {
        fail("ada@example.com", 5);
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThatCode(() -> limiter.checkAllowed("ada@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("failures spread over more than the window don't add up")
    void oldFailuresExpire() {
        fail("ada@example.com", 4);
        clock.advance(Duration.ofMinutes(16));
        fail("ada@example.com", 4);
        assertThatCode(() -> limiter.checkAllowed("ada@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a successful sign-in resets the counter; addresses are independent")
    void successResetsAndAddressesAreIndependent() {
        fail("ada@example.com", 4);
        limiter.recordSuccess("ada@example.com");
        fail("ada@example.com", 4);
        assertThatCode(() -> limiter.checkAllowed("ada@example.com")).doesNotThrowAnyException();

        fail("grace@example.com", 5);
        assertThatCode(() -> limiter.checkAllowed("ada@example.com")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAllowed("grace@example.com")).isInstanceOf(RateLimitExceededException.class);
    }
}
