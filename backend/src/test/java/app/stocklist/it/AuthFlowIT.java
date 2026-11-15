package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthFlowIT extends IntegrationTestBase {
    private static final String PW = "Green-Mango-42";

    @Test
    void ownerRegistersAndSignsIn() {
        String u = unique("own");
        Resp reg = registerBusiness(u, PW);
        assertEquals(201, reg.status(), reg.raw());
        assertNotNull(reg.str("accessToken"));
        assertNotNull(reg.str("refreshToken"));
        assertEquals("OWNER", text(reg.body().get("user").get("role")));
        assertEquals(25, reg.body().get("user").get("permissions").size(), "owner holds every permission");

        Resp login = login(u.toUpperCase(), PW);
        assertEquals(200, login.status(), "usernames are case-insensitive: " + login.raw());
        Resp me = get("/api/v1/auth/me", login.str("accessToken"));
        assertEquals(200, me.status());
        assertEquals(u, me.str("username"));
    }

    @Test
    void duplicateUsernameIsRejected() {
        String u = unique("dup");
        assertEquals(201, registerBusiness(u, PW).status());
        Resp again = registerBusiness(u, PW);
        assertEquals(409, again.status());
        assertEquals("USERNAME_TAKEN", again.str("code"));
    }

    @Test
    void passwordsAreStoredAsArgon2idHashes() {
        String u = unique("hash");
        registerBusiness(u, PW);
        String hash = jdbc.queryForObject("select password_hash from app_user where username = ?", String.class, u);
        assertTrue(hash.startsWith("$argon2id$"), hash);
        assertFalse(hash.contains(PW));
    }

    @Test
    void weakPasswordIsRejected() {
        Resp r = registerBusiness(unique("weak"), "password123");
        assertEquals(400, r.status());
        assertEquals("WEAK_PASSWORD", r.str("code"));
    }

    @Test
    void requestsWithoutValidTokenAreRejected() {
        assertEquals(401, get("/api/v1/auth/me", null).status());
        assertEquals(401, get("/api/v1/auth/me", "garbage.token.value").status());
    }

    @Test
    void refreshRotatesAndReuseRevokesTheWholeFamily() {
        String u = unique("rot");
        Resp s = registerBusiness(u, PW);
        String r1 = s.str("refreshToken");

        Resp first = post("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(r1));
        assertEquals(200, first.status(), first.raw());
        String r2 = first.str("refreshToken");
        assertFalse(r1.equals(r2));

        Resp reuse = post("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(r1));
        assertEquals(401, reuse.status(), "an already-used refresh token is refused");

        Resp afterReuse = post("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(r2));
        assertEquals(401, afterReuse.status(), "reuse ends the whole session family");

        Integer events = jdbc.queryForObject(
                "select count(*) from activity_log a join app_user u on u.id = a.user_id "
                        + "where u.username = ? and a.action = 'TOKEN_REUSE_DETECTED'", Integer.class, u);
        assertEquals(1, events);
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() {
        String u = unique("lock");
        registerBusiness(u, PW);
        for (int i = 0; i < 5; i++) {
            Resp bad = login(u, "Wrong-Password-" + i);
            assertEquals(401, bad.status());
            assertEquals("INVALID_CREDENTIALS", bad.str("code"));
        }
        Resp correct = login(u, PW);
        assertEquals(401, correct.status(), "locked for 15 minutes even with the right password");
        assertNotNull(jdbc.queryForObject("select locked_until from app_user where username = ?",
                java.sql.Timestamp.class, u));
        Integer failures = jdbc.queryForObject(
                "select count(*) from activity_log a join app_user u on u.id = a.user_id "
                        + "where u.username = ? and a.action = 'LOGIN_FAILED'", Integer.class, u);
        assertEquals(6, failures);
    }

    @Test
    void unknownUsernameGetsTheSameErrorAsWrongPassword() {
        Resp r = login(unique("nobody"), PW);
        assertEquals(401, r.status());
        assertEquals("INVALID_CREDENTIALS", r.str("code"));
    }

    @Test
    void logoutEndsTheSession() {
        String u = unique("out");
        Resp s = registerBusiness(u, PW);
        Resp out = post("/api/v1/auth/logout", "{\"refreshToken\":\"%s\"}".formatted(s.str("refreshToken")),
                s.str("accessToken"));
        assertEquals(204, out.status(), out.raw());
        assertEquals(401, post("/api/v1/auth/refresh",
                "{\"refreshToken\":\"%s\"}".formatted(s.str("refreshToken"))).status());
    }

    @Test
    void changingPasswordSignsOutOtherDevices() {
        String u = unique("chg");
        registerBusiness(u, PW);
        Resp phoneA = login(u, PW);
        Resp phoneB = login(u, PW);

        Resp changed = post("/api/v1/auth/password/change",
                "{\"currentPassword\":\"%s\",\"newPassword\":\"Blue-Lotus-77\"}".formatted(PW), phoneA.str("accessToken"));
        assertEquals(200, changed.status(), changed.raw());

        assertEquals(401, get("/api/v1/auth/me", phoneB.str("accessToken")).status(), "old access tokens die");
        assertEquals(401, post("/api/v1/auth/refresh",
                "{\"refreshToken\":\"%s\"}".formatted(phoneB.str("refreshToken"))).status());
        assertEquals(200, get("/api/v1/auth/me", changed.str("accessToken")).status(), "this phone keeps working");
        assertEquals(200, login(u, "Blue-Lotus-77").status());
    }

    @Test
    void ownerResetsForgottenPasswordWithEmailedToken() {
        String u = unique("rst");
        registerBusiness(u, PW);
        assertEquals(202, post("/api/v1/auth/password/forgot", "{\"username\":\"%s\"}".formatted(u)).status());
        assertEquals(202, post("/api/v1/auth/password/forgot",
                "{\"username\":\"%s\"}".formatted(unique("ghost"))).status(), "no hint whether a user exists");

        String token = mailer.lastTokenByUsername.get(u);
        assertNotNull(token);
        Resp reset = post("/api/v1/auth/password/reset",
                "{\"token\":\"%s\",\"newPassword\":\"Red-Kite-2026\"}".formatted(token));
        assertEquals(204, reset.status(), reset.raw());
        assertEquals(400, post("/api/v1/auth/password/reset",
                "{\"token\":\"%s\",\"newPassword\":\"Another-One-99\"}".formatted(token)).status(), "single use");
        assertEquals(200, login(u, "Red-Kite-2026").status());
        assertEquals(401, login(u, PW).status());
    }

    @Test
    void stepUpRequiresTheCorrectPassword() {
        String u = unique("stp");
        Resp s = registerBusiness(u, PW);
        assertEquals(401, post("/api/v1/auth/reauth", "{\"password\":\"nope-nope\"}", s.str("accessToken")).status());
        assertEquals(200, post("/api/v1/auth/reauth", "{\"password\":\"%s\"}".formatted(PW),
                s.str("accessToken")).status());
    }
}
