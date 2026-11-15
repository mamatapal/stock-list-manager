package app.stocklist.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Duration refreshTtl,
        Lockout lockout,
        int loginRatePerMinute,
        Duration resetTokenTtl,
        boolean mailLogTokens) {

    public record Jwt(String signingKey, String keyId, Duration accessTtl, Duration stepUpTtl) {}

    public record Lockout(int maxFailures, Duration duration) {}
}
