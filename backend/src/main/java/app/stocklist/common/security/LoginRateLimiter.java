package app.stocklist.common.security;

import app.stocklist.config.AppProperties;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Sliding one-minute window per client IP for password attempts (login and re-authentication). */
@Component
public class LoginRateLimiter {
    private static final long WINDOW_MS = 60_000;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    @Autowired
    public LoginRateLimiter(AppProperties props, Clock clock) {
        this(props.loginRatePerMinute(), clock);
    }

    LoginRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    public boolean tryAcquire(String ip) {
        long now = clock.millis();
        Deque<Long> q = attempts.computeIfAbsent(ip == null ? "unknown" : ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
