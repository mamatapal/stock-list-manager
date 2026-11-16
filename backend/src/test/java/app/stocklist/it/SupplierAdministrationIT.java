package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.stocklist.common.error.ApiException;
import app.stocklist.supplieraccount.SupplierAccess;
import app.stocklist.supplieraccount.SupplierPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Supplier Administration portal: administrator sign-in with authenticator codes and supplier review (Phase S1). */
class SupplierAdministrationIT extends SupplierTestSupport {
    private static final String ADMIN = "/api/v1/admin";

    @Autowired
    SupplierAccess supplierAccess;

    @Test
    void thePortalPageIsServedWithStrictSecurityHeaders() {
        Http redirect = http("GET", "/admin", null, null, false, null);
        assertEquals(302, redirect.status());
        assertTrue(redirect.header("Location").endsWith("/admin/index.html"), redirect.header("Location"));
        Http page = http("GET", "/admin/index.html", null, null, false, null);
        assertEquals(200, page.status());
        assertTrue(page.raw().contains("Supplier Administration"));
        String csp = page.header("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("frame-ancestors 'none'") && csp.contains("script-src 'self'"), csp);
        assertEquals(200, http("GET", "/admin/admin.js", null, null, false, null).status());
        Http linkPage = http("GET", "/supplier/reset-password.html", null, null, false, null);
        assertEquals(200, linkPage.status());
        assertEquals("no-referrer", linkPage.header("Referrer-Policy"));
    }

    @Test
    void signInNeedsPasswordAndAFreshAuthenticatorCode() {
        Admin a = newAdmin();
        Instant now = Instant.now();
        Http wrongCode = adminLogin(a, a.code(now.minusSeconds(600)));
        assertEquals(401, wrongCode.status());
        assertEquals("INVALID_CREDENTIALS", wrongCode.str("code"));
        Http noCode = adminLogin(a, null);
        assertEquals(401, noCode.status());

        // The sign-in itself must come from the portal page (header check).
        Http forged = http("POST", ADMIN + "/auth/login", """
                {"username":"%s","password":"%s","code":"%s"}""".formatted(a.username(), ADMIN_PW, a.codeNow()),
                null, false, null);
        assertEquals(403, forged.status());

        String code = a.code(now);
        Http ok = adminLogin(a, code);
        assertEquals(200, ok.status(), ok.raw());
        String cookie = ok.header("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict")
                && cookie.contains("Path=/api/v1/admin"), cookie);
        assertEquals(a.username(), ok.str("username"));

        // The same code cannot be used twice.
        assertEquals(401, adminLogin(a, code).status());

        String session = cookieValue(cookie);
        Http me = adminGet(ADMIN + "/auth/me", session);
        assertEquals(200, me.status(), me.raw());
    }

    @Test
    void fiveFailuresLockTheAdministrator() {
        Admin a = newAdmin();
        for (int i = 0; i < 5; i++) {
            assertEquals(401, http("POST", ADMIN + "/auth/login", """
                    {"username":"%s","password":"wrong-password-%d","code":"000000"}""".formatted(a.username(), i),
                    null, true, null).status());
        }
        assertEquals(401, adminLogin(a, a.codeNow()).status(), "locked even with the right password and code");
    }

    @Test
    void everyReviewEndpointNeedsASignedInAdministrator() {
        Resp supplier = verifiedSupplier(supplierEmail());
        String id = orgId(supplier);
        assertEquals(401, adminGet(ADMIN + "/suppliers", null).status());
        assertEquals(401, adminGet(ADMIN + "/suppliers/" + id, "not-a-session").status());
        assertEquals(401, http("POST", ADMIN + "/suppliers/" + id + "/approve", null, null, true, 1L).status());
        // Bearer tokens of shop or supplier users mean nothing here.
        assertEquals(401, call("GET", ADMIN + "/suppliers", null, supplier.str("accessToken"), null).status());

        // A signed-in administrator still needs the portal header for changes.
        String session = signIn(newAdmin());
        Http noHeader = http("POST", ADMIN + "/suppliers/" + id + "/approve", null, session, false,
                orgVersion(session, id));
        assertEquals(403, noHeader.status());
        assertEquals("CSRF_HEADER_MISSING", noHeader.str("code"));
    }

    @Test
    void approveSuspendAndReinstateWithHistoryAndEmails() {
        String email = supplierEmail();
        Resp supplier = verifiedSupplier(email);
        String id = orgId(supplier);
        String session = signIn(newAdmin());

        Http pending = adminGet(ADMIN + "/suppliers?status=PENDING_APPROVAL", session);
        assertEquals(200, pending.status(), pending.raw());
        assertTrue(pending.raw().contains(id));
        assertEquals("INVALID_STATUS_FILTER", adminGet(ADMIN + "/suppliers?status=NOPE", session).str("code"));

        Http detail = adminGet(ADMIN + "/suppliers/" + id, session);
        assertEquals("Ramesh Agarwal", text(detail.body().get("contactPerson")));
        assertEquals("REGISTERED", text(detail.body().at("/history/0/action")));
        assertEquals("EMAIL_VERIFIED", text(detail.body().at("/history/1/action")));
        long version = orgVersion(session, id);

        Http stale = adminPost(ADMIN + "/suppliers/" + id + "/approve", null, session, version + 3);
        assertEquals(409, stale.status());
        assertEquals("STALE_VERSION", stale.str("code"));
        assertEquals("WRONG_STATUS", adminPost(ADMIN + "/suppliers/" + id + "/suspend", "{\"reason\":\"x\"}",
                session, version).str("code"), "only approved suppliers can be suspended");

        Http approved = adminPost(ADMIN + "/suppliers/" + id + "/approve", null, session, version);
        assertEquals(200, approved.status(), approved.raw());
        assertEquals("APPROVED", text(approved.body().at("/summary/status")));
        assertEquals("APPROVED", supplierMail.lastStatus.get(email));
        UUID orgId = UUID.fromString(id);
        UUID userId = UUID.fromString(text(supplier.at("/me/user/id")));
        supplierAccess.requireApproved(new SupplierPrincipal(userId, orgId, null));

        // After approval the supplier cannot change the reviewed details any more.
        Resp me = supplierMe(supplier.str("accessToken"));
        assertEquals("false", text(me.at("/org/canEditDetails")));
        Resp locked = callWithHeader("PUT", PORTAL + "/profile", profileJson(), supplier.str("accessToken"),
                "If-Match", text(me.at("/org/version")));
        assertEquals("DETAILS_LOCKED", locked.str("code"));

        long v2 = Long.parseLong(text(approved.body().at("/summary/version")));
        assertEquals("REASON_REQUIRED", adminPost(ADMIN + "/suppliers/" + id + "/suspend", "{\"reason\":\" \"}",
                session, v2).str("code"));
        Http suspended = adminPost(ADMIN + "/suppliers/" + id + "/suspend",
                "{\"reason\":\"GST registration cancelled\"}", session, v2);
        assertEquals(200, suspended.status(), suspended.raw());
        assertEquals("SUSPENDED", supplierMail.lastStatus.get(email));
        Resp seen = supplierMe(supplier.str("accessToken"));
        assertEquals("SUSPENDED", text(seen.at("/org/status")));
        assertEquals("GST registration cancelled", text(seen.at("/org/statusReason")));
        ApiException blocked = assertThrows(ApiException.class,
                () -> supplierAccess.requireApproved(new SupplierPrincipal(userId, orgId, null)));
        assertEquals("SUPPLIER_SUSPENDED", blocked.code());

        long v3 = Long.parseLong(text(suspended.body().at("/summary/version")));
        Http back = adminPost(ADMIN + "/suppliers/" + id + "/reinstate", null, session, v3);
        assertEquals(200, back.status(), back.raw());
        assertEquals("APPROVED", text(back.body().at("/summary/status")));
        JsonNode history = back.body().get("history");
        assertEquals("REINSTATED", text(history.get(history.size() - 1).get("action")));
        assertNotNull(text(history.get(history.size() - 1).get("actorName")));
    }

    @Test
    void aRejectedSupplierCorrectsTheDetailsAndIsReviewedAgain() {
        String email = supplierEmail();
        Resp supplier = verifiedSupplier(email);
        String id = orgId(supplier);
        String session = signIn(newAdmin());
        Http rejected = adminPost(ADMIN + "/suppliers/" + id + "/reject",
                "{\"reason\":\"The GSTIN does not match the business name\"}", session, orgVersion(session, id));
        assertEquals(200, rejected.status(), rejected.raw());
        assertEquals("REJECTED", supplierMail.lastStatus.get(email));

        Resp me = supplierMe(supplier.str("accessToken"));
        assertEquals("REJECTED", text(me.at("/org/status")));
        assertEquals("The GSTIN does not match the business name", text(me.at("/org/statusReason")));
        assertEquals("true", text(me.at("/org/canEditDetails")));

        Resp resubmitted = callWithHeader("PUT", PORTAL + "/profile", profileJson(), supplier.str("accessToken"),
                "If-Match", text(me.at("/org/version")));
        assertEquals(200, resubmitted.status(), resubmitted.raw());
        assertEquals("PENDING_APPROVAL", text(resubmitted.at("/org/status")));
        assertEquals(null, text(resubmitted.at("/org/statusReason")));

        Http detail = adminGet(ADMIN + "/suppliers/" + id, session);
        JsonNode history = detail.body().get("history");
        assertEquals("RESUBMITTED", text(history.get(history.size() - 1).get("action")));
    }

    @Test
    void verificationThroughAPasswordResetIsFlaggedForTheReviewer() {
        String email = supplierEmail();
        registerSupplier(email);
        post(PORTAL + "/auth/password/forgot", "{\"email\":\"" + email + "\"}");
        post(PORTAL + "/auth/password/reset", "{\"token\":\"" + token(supplierMail.resetLinks.get(email))
                + "\",\"newPassword\":\"Owner-Pass-2026\"}");
        Resp owner = supplierLogin(email, "Owner-Pass-2026");
        String session = signIn(newAdmin());
        Http d = adminGet(ADMIN + "/suppliers/" + orgId(owner), session);
        assertEquals("true", text(d.body().get("emailVerifiedByReset")), d.raw());
        assertNotNull(text(d.body().at("/history/1/ip")));
        Http normal = adminGet(ADMIN + "/suppliers/" + orgId(verifiedSupplier(supplierEmail())), session);
        assertEquals("false", text(normal.body().get("emailVerifiedByReset")));
    }

    @Test
    void possibleDuplicatesAreFlaggedForTheReviewer() {
        Resp first = verifiedSupplier(supplierEmail());
        Resp second = verifiedSupplier(supplierEmail());
        String session = signIn(newAdmin());
        Http d = adminGet(ADMIN + "/suppliers/" + orgId(second), session);
        assertTrue(Long.parseLong(text(d.body().get("otherSuppliersWithSameGstin"))) >= 1, d.raw());
        assertTrue(Long.parseLong(text(d.body().get("otherSuppliersWithSameMobile"))) >= 1, d.raw());
        assertEquals(200, adminGet(ADMIN + "/suppliers/" + orgId(first), session).status());
    }

    @Test
    void sessionsEndOnSignOutIdleTimeAndDeactivation() {
        Admin a = newAdmin();
        String session = signIn(a);
        assertEquals(204, http("POST", ADMIN + "/auth/logout", null, session, true, null).status());
        assertEquals(401, adminGet(ADMIN + "/auth/me", session).status());

        Admin b = newAdmin();
        String idle = signIn(b);
        jdbc.update("update platform_admin_session set last_seen_at = now(6) - interval 31 minute "
                + "where admin_id = (select id from platform_admin where username = ?)", b.username());
        assertEquals(401, adminGet(ADMIN + "/auth/me", idle).status());

        Admin c = newAdmin();
        String active = signIn(c);
        adminAuth.deactivateAdmin(c.username());
        assertEquals(401, adminGet(ADMIN + "/auth/me", active).status());
    }

    @Test
    void administratorAccountsAreCheckedWhenCreated() {
        assertThrows(IllegalArgumentException.class,
                () -> adminAuth.createAdmin("ab", "Too Short", null, ADMIN_PW));
        assertThrows(IllegalArgumentException.class,
                () -> adminAuth.createAdmin(unique("adm"), "Weak", null, "short-pass"));
        Admin a = newAdmin();
        assertThrows(IllegalArgumentException.class,
                () -> adminAuth.createAdmin(a.username(), "Again", null, ADMIN_PW));
        String stored = jdbc.queryForObject("select totp_secret from platform_admin where username = ?",
                String.class, a.username());
        assertTrue(stored.startsWith("v1:") && !stored.contains(a.secret()), "authenticator key is stored encrypted");
    }

    private static String profileJson() {
        return """
                {"legalName":"Agarwal Wholesale Pvt Ltd","tradeName":"Agarwal Traders","contactPerson":"Ramesh Agarwal",
                 "mobile":"9876543210","address":"12 Station Road, Near Bus Stand","city":"Indore",
                 "state":"Madhya Pradesh","pinCode":"452001","gstin":"23AAPCA1234B1Z5"}""";
    }
}
