package app.stocklist.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.stocklist.TestSupport;
import app.stocklist.TestSupport.MutableClock;
import app.stocklist.config.AppProperties;
import app.stocklist.user.AppUser;
import app.stocklist.user.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-14T05:00:00Z"));
    private final JwtService jwt = new JwtService(TestSupport.props(), clock);
    private final AppUser user = new AppUser(UUID.randomUUID(), "ravi", "Ravi", null, null, "x", Role.STAFF, false,
            null, clock.instant());

    @Test
    void accessTokenRoundTrips() {
        String token = jwt.issueAccess(user, "device-1234").token();
        TokenClaims c = jwt.verify(token, TokenClaims.SCOPE_ACCESS).orElseThrow();
        assertEquals(user.getId(), c.userId());
        assertEquals(user.getBusinessId(), c.businessId());
        assertEquals(0, c.tokenVersion());
        assertEquals("device-1234", c.deviceId());
    }

    @Test
    void rejectsExpiredTamperedAndWrongScopeTokens() {
        String access = jwt.issueAccess(user, "device-1234").token();
        String stepUp = jwt.issueStepUp(user, "device-1234").token();

        assertTrue(jwt.verify(stepUp, TokenClaims.SCOPE_ACCESS).isEmpty(), "step-up token is not an access token");
        assertTrue(jwt.verify(access, TokenClaims.SCOPE_STEP_UP).isEmpty(), "access token is not a step-up token");

        String[] parts = access.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];
        assertTrue(jwt.verify(tampered, TokenClaims.SCOPE_ACCESS).isEmpty());
        assertTrue(jwt.verify("not-a-jwt", TokenClaims.SCOPE_ACCESS).isEmpty());

        clock.advance(Duration.ofMinutes(16));
        assertTrue(jwt.verify(access, TokenClaims.SCOPE_ACCESS).isEmpty(), "15-minute access token has expired");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        AppProperties other = new AppProperties(
                new AppProperties.Jwt(java.util.Base64.getEncoder().encodeToString(new byte[48]), "k2",
                        Duration.ofMinutes(15), Duration.ofMinutes(5)),
                Duration.ofDays(30), new AppProperties.Lockout(5, Duration.ofMinutes(15)), 20,
                Duration.ofMinutes(30), false);
        String foreign = new JwtService(other, clock).issueAccess(user, "device-1234").token();
        assertTrue(jwt.verify(foreign, TokenClaims.SCOPE_ACCESS).isEmpty());
    }

    @Test
    void refusesShortSigningKey() {
        AppProperties weak = new AppProperties(
                new AppProperties.Jwt(java.util.Base64.getEncoder().encodeToString(new byte[16]), "k1",
                        Duration.ofMinutes(15), Duration.ofMinutes(5)),
                Duration.ofDays(30), new AppProperties.Lockout(5, Duration.ofMinutes(15)), 20,
                Duration.ofMinutes(30), false);
        assertThrows(IllegalStateException.class, () -> new JwtService(weak, clock));
    }
}
