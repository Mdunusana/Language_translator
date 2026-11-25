package com.linguavoice.security;

import com.linguavoice.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Brute-force protection for sign-in: after {@code maxFailures} wrong passwords for one email address within
 * the window, that address is locked for the window, even with the right password, and gets HTTP 429.
 *
 * <ul>
 *   <li>Keyed by email, so password-guessing against one account is stopped whatever IP it comes from.</li>
 *   <li>Unknown emails are counted exactly like real ones, so the lockout can't reveal which emails exist.</li>
 *   <li>A successful sign-in clears the counter.</li>
 *   <li>Trade-off: someone could deliberately lock a known address for 15 minutes; the lock expires by itself.</li>
 *   <li>In memory, single instance; a multi-instance deployment would move this to Redis.</li>
 * </ul>
 */
@Component
public class LoginAttemptLimiter {

    private static final int EVICTION_THRESHOLD = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(@Value("${app.security.login-max-failures:5}") int maxFailures,
                               @Value("${app.security.login-lockout-minutes:15}") int lockoutMinutes,
                               Clock clock) {
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(lockoutMinutes);
        this.clock = clock;
    }

    /** @throws RateLimitExceededException while the address is locked */
    public void checkAllowed(String normalizedEmail) {
        Attempts entry = attempts.get(normalizedEmail);
        long now = clock.millis();
        if (entry != null && entry.lockedUntil > now) {
            long seconds = Math.max(1, (entry.lockedUntil - now + 999) / 1000);
            long minutes = (seconds + 59) / 60;
            throw new RateLimitExceededException(seconds,
                    "Too many failed sign-in attempts. Try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
        }
    }

    public void recordFailure(String normalizedEmail) {
        long now = clock.millis();
        if (attempts.size() > EVICTION_THRESHOLD) {
            attempts.entrySet().removeIf(e -> now - e.getValue().firstFailure > window.toMillis() && e.getValue().lockedUntil < now);
        }
        attempts.compute(normalizedEmail, (key, entry) -> {
            Attempts current = entry == null || now - entry.firstFailure > window.toMillis() ? new Attempts(now) : entry;
            current.failures++;
            if (current.failures >= maxFailures) {
                current.lockedUntil = now + window.toMillis();
            }
            return current;
        });
    }

    public void recordSuccess(String normalizedEmail) {
        attempts.remove(normalizedEmail);
    }

    private static final class Attempts {
        private final long firstFailure;
        private int failures;
        private long lockedUntil;

        Attempts(long firstFailure) {
            this.firstFailure = firstFailure;
        }
    }
}
