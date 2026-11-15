package app.stocklist.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.stocklist.TestSupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    @Test
    void allowsLimitPerMinutePerIp() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-14T05:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(3, clock);
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.2"), "other IPs are unaffected");
        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
    }
}
