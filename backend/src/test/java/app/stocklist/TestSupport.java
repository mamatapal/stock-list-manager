package app.stocklist;

import app.stocklist.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;

public final class TestSupport {
    private TestSupport() {}

    public static final String SIGNING_KEY = Base64.getEncoder()
            .encodeToString("test-signing-key-test-signing-key-0123456789".getBytes());

    public static AppProperties props() {
        return new AppProperties(
                new AppProperties.Jwt(SIGNING_KEY, "k1", Duration.ofMinutes(15), Duration.ofMinutes(5)),
                Duration.ofDays(30),
                new AppProperties.Lockout(5, Duration.ofMinutes(15)),
                20,
                Duration.ofMinutes(30),
                false);
    }

    /** A clock tests can move forward. */
    public static final class MutableClock extends Clock {
        private Instant now;

        public MutableClock(Instant start) { this.now = start; }

        public void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
