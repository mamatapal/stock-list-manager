package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Supplier Management app API: registration, email links, sign-in, sessions and business details (Phase S1). */
class SupplierPortalIT extends SupplierTestSupport {

    @Test
    void registerVerifyByLinkAndWaitForApproval() {
        String email = supplierEmail();
        Resp reg = registerSupplier(email);
        assertEquals(202, reg.status(), reg.raw());
        String link = supplierMail.verifyLinks.get(email);
        assertTrue(link.startsWith("https://test.example/supplier/verify-email.html#t="), link);

        // Signing in works before verification, and shows what is still needed.
        Resp early = supplierLogin(email, SUPPLIER_PW);
        assertEquals(200, early.status(), early.raw());
        assertEquals("PENDING_EMAIL_VERIFICATION", text(early.at("/me/org/status")));
        assertEquals("false", text(early.at("/me/user/emailVerified")));

        // Opening the page does not verify (email scanners open links); only the button's POST does.
        Resp page = call("GET", "/supplier/verify-email.html", null, null, null);
        assertEquals(200, page.status());
        assertEquals("PENDING_EMAIL_VERIFICATION", text(supplierMe(early.str("accessToken")).at("/org/status")));

        Resp v = verify(token(link));
        assertEquals(200, v.status(), v.raw());
        assertEquals("PENDING_APPROVAL", v.str("status"));
        // A second click on the same link is harmless.
        assertEquals(200, verify(token(link)).status());

        Resp me = supplierMe(early.str("accessToken"));
        assertEquals(200, me.status(), me.raw());
        assertEquals("PENDING_APPROVAL", text(me.at("/org/status")));
        assertEquals("true", text(me.at("/user/emailVerified")));
        assertEquals("true", text(me.at("/org/canEditDetails")));
        assertEquals("23AAPCA1234B1Z5", text(me.at("/org/gstin")));
        assertEquals(email, text(me.at("/user/email")));
    }

    @Test
    void registeringAnExistingEmailRevealsNothingAndCreatesNothing() {
        String email = supplierEmail();
        assertEquals(202, registerSupplier(email).status());
        Integer before = jdbc.queryForObject("select count(*) from supplier_org", Integer.class);
        Resp again = registerSupplier(email.toUpperCase());
        assertEquals(202, again.status(), again.raw());
        assertEquals(before, jdbc.queryForObject("select count(*) from supplier_org", Integer.class));
        assertNotNull(supplierMail.alreadyRegistered.get(email), "the owner is told by email instead");
    }

    @Test
    void inputIsCheckedWithCodesTheAppTranslates() {
        String e = supplierEmail();
        assertEquals("INVALID_MOBILE", post(PORTAL + "/auth/register",
                registerJson(e, "Shop", "12345", null, SUPPLIER_PW)).str("code"));
        assertEquals("INVALID_GSTIN", post(PORTAL + "/auth/register",
                registerJson(e, "Shop", "9876543210", "NOTAGSTIN", SUPPLIER_PW)).str("code"));
        assertEquals("WEAK_PASSWORD", post(PORTAL + "/auth/register",
                registerJson(e, "Shop", "9876543210", null, "short")).str("code"));
        assertEquals("NAME_REQUIRED", post(PORTAL + "/auth/register",
                registerJson(e, " ", "9876543210", null, SUPPLIER_PW)).str("code"));
        assertEquals("INVALID_EMAIL", post(PORTAL + "/auth/register",
                registerJson("not-an-email", "Shop", "9876543210", null, SUPPLIER_PW)).str("code"));

        // Hindi names, +91 mobile numbers and a GSTIN in small letters are accepted and cleaned.
        String hindi = supplierEmail();
        Resp ok = post(PORTAL + "/auth/register",
                registerJson(hindi, "अग्रवाल ट्रेडर्स", "+91 98765 43210", "23aapca1234b1z5", SUPPLIER_PW));
        assertEquals(202, ok.status(), ok.raw());
        verify(token(supplierMail.verifyLinks.get(hindi)));
        Resp me = supplierMe(supplierLogin(hindi, SUPPLIER_PW).str("accessToken"));
        assertEquals("अग्रवाल ट्रेडर्स", text(me.at("/org/legalName")));
        assertEquals("9876543210", text(me.at("/org/mobile")));
        assertEquals("23AAPCA1234B1Z5", text(me.at("/org/gstin")));
    }

    @Test
    void linksAreSingleUseExpireAndCannotBeSwapped() {
        String email = supplierEmail();
        registerSupplier(email);
        String verifyToken = token(supplierMail.verifyLinks.get(email));

        assertEquals("LINK_INVALID", verify("made-up-token").str("code"));
        // A verification link is not a password reset link.
        assertEquals("LINK_INVALID", post(PORTAL + "/auth/password/reset",
                "{\"token\":\"" + verifyToken + "\",\"newPassword\":\"Another-Pass-55\"}").str("code"));

        jdbc.update("update supplier_email_token set expires_at = now(6) - interval 1 minute where user_id = "
                + "(select id from supplier_user where email = ?)", email);
        assertEquals("LINK_EXPIRED", verify(verifyToken).str("code"));

        // Asking again within a minute sends nothing new; after a minute a new link replaces the old one.
        assertEquals(202, post(PORTAL + "/auth/resend-verification", "{\"email\":\"" + email + "\"}").status());
        assertEquals(verifyToken, token(supplierMail.verifyLinks.get(email)));
        jdbc.update("update supplier_email_token set created_at = now(6) - interval 2 minute where user_id = "
                + "(select id from supplier_user where email = ?)", email);
        assertEquals(202, post(PORTAL + "/auth/resend-verification", "{\"email\":\"" + email + "\"}").status());
        String fresh = token(supplierMail.verifyLinks.get(email));
        assertNotEquals(verifyToken, fresh);
        assertEquals(200, verify(fresh).status());

        // Unknown addresses get the same answer.
        assertEquals(202, post(PORTAL + "/auth/resend-verification", "{\"email\":\"nobody@example.com\"}").status());
        assertEquals(202, post(PORTAL + "/auth/password/forgot", "{\"email\":\"nobody@example.com\"}").status());
    }

    @Test
    void wrongPasswordsLockTheAccountAndAllFailuresLookTheSame() {
        String email = supplierEmail();
        registerSupplier(email);
        Resp unknown = supplierLogin("nobody-" + email, SUPPLIER_PW);
        assertEquals(401, unknown.status());
        assertEquals("INVALID_CREDENTIALS", unknown.str("code"));
        for (int i = 0; i < 5; i++) {
            assertEquals("INVALID_CREDENTIALS", supplierLogin(email, "Wrong-Pass-" + i).str("code"));
        }
        Resp locked = supplierLogin(email, SUPPLIER_PW);
        assertEquals(401, locked.status(), "locked even with the right password");
        assertEquals(unknown.str("detail"), locked.str("detail"));
    }

    @Test
    void refreshTokensRotateAndAReusedOneEndsTheSession() {
        Resp s = verifiedSupplier(supplierEmail());
        String first = s.str("refreshToken");
        Resp r1 = post(PORTAL + "/auth/refresh", "{\"refreshToken\":\"" + first + "\"}");
        assertEquals(200, r1.status(), r1.raw());
        String second = r1.str("refreshToken");
        assertEquals(401, post(PORTAL + "/auth/refresh", "{\"refreshToken\":\"" + first + "\"}").status());
        assertEquals(401, post(PORTAL + "/auth/refresh", "{\"refreshToken\":\"" + second + "\"}").status(),
                "reusing an old token ends the whole session");
    }

    @Test
    void passwordResetByLinkSignsOutEveryPhoneAndProvesTheEmail() {
        String email = supplierEmail();
        registerSupplier(email);
        Resp before = supplierLogin(email, SUPPLIER_PW);
        assertEquals(202, post(PORTAL + "/auth/password/forgot", "{\"email\":\"" + email + "\"}").status());
        String t = token(supplierMail.resetLinks.get(email));
        Resp weak = post(PORTAL + "/auth/password/reset", "{\"token\":\"" + t + "\",\"newPassword\":\"12345678\"}");
        assertEquals("WEAK_PASSWORD", weak.str("code"));
        Resp ok = post(PORTAL + "/auth/password/reset", "{\"token\":\"" + t + "\",\"newPassword\":\"Fresh-Pass-2026\"}");
        assertEquals(204, ok.status(), ok.raw());
        assertEquals("LINK_USED", post(PORTAL + "/auth/password/reset",
                "{\"token\":\"" + t + "\",\"newPassword\":\"Fresh-Pass-2027\"}").str("code"));

        assertEquals(401, supplierMe(before.str("accessToken")).status(), "old access token stops working");
        assertEquals(401, post(PORTAL + "/auth/refresh",
                "{\"refreshToken\":\"" + before.str("refreshToken") + "\"}").status());
        Resp after = supplierLogin(email, "Fresh-Pass-2026");
        assertEquals(200, after.status(), after.raw());
        assertEquals("PENDING_APPROVAL", text(after.at("/me/org/status")), "the emailed link proved the address");
    }

    @Test
    void changePasswordKeepsThisPhoneSignedIn() {
        String email = supplierEmail();
        Resp s = verifiedSupplier(email);
        String body = "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}";
        assertEquals("WRONG_CURRENT_PASSWORD", post(PORTAL + "/auth/password/change",
                body.formatted("nope-nope-1", "Next-Pass-2026"), s.str("accessToken")).str("code"));
        assertEquals("SAME_PASSWORD", post(PORTAL + "/auth/password/change",
                body.formatted(SUPPLIER_PW, SUPPLIER_PW), s.str("accessToken")).str("code"));
        Resp changed = post(PORTAL + "/auth/password/change", body.formatted(SUPPLIER_PW, "Next-Pass-2026"),
                s.str("accessToken"));
        assertEquals(200, changed.status(), changed.raw());
        assertEquals(200, supplierMe(changed.str("accessToken")).status());
        assertEquals(401, supplierMe(s.str("accessToken")).status());
    }

    @Test
    void supplierAndShopTokensAreNeverInterchangeable() {
        Resp supplier = verifiedSupplier(supplierEmail());
        String supplierToken = supplier.str("accessToken");
        String owner = unique("own");
        String shopToken = registerBusiness(owner, "Green-Mango-42").str("accessToken");

        assertEquals(401, get("/api/v1/auth/me", supplierToken).status());
        assertEquals(401, get("/api/v1/suppliers", supplierToken).status());
        assertEquals(401, get("/api/v1/purchases", supplierToken).status());
        assertEquals(401, supplierMe(shopToken).status());
        assertEquals(401, get("/api/v1/admin/suppliers", supplierToken).status());
        assertEquals(401, get("/api/v1/admin/suppliers", shopToken).status());
        // And each still works where it belongs.
        assertEquals(200, get("/api/v1/auth/me", shopToken).status());
        assertEquals(200, supplierMe(supplierToken).status());
    }

    @Test
    void detailsCanBeChangedWhileWaitingWithTheCurrentVersion() {
        Resp s = verifiedSupplier(supplierEmail());
        String token = s.str("accessToken");
        long version = Long.parseLong(text(s.at("/me/org/version")));
        String profile = """
                {"legalName":"Agarwal Wholesale LLP","tradeName":null,"contactPerson":"Suresh Agarwal",
                 "mobile":"9123456780","address":"14 Station Road","city":"Indore","state":"Madhya Pradesh",
                 "pinCode":"452002","gstin":null}""";
        Resp stale = callWithHeader("PUT", PORTAL + "/profile", profile, token, "If-Match", Long.toString(version + 5));
        assertEquals(409, stale.status());
        assertEquals("STALE_VERSION", stale.str("code"));
        Resp ok = callWithHeader("PUT", PORTAL + "/profile", profile, token, "If-Match", Long.toString(version));
        assertEquals(200, ok.status(), ok.raw());
        assertEquals("Agarwal Wholesale LLP", text(ok.at("/org/legalName")));
        assertNull(text(ok.at("/org/gstin")));
        assertEquals("PENDING_APPROVAL", text(ok.at("/org/status")));
        assertFalse(ok.raw().contains("passwordHash"));
    }

    @Test
    void logoutEndsThisPhonesSession() {
        Resp s = verifiedSupplier(supplierEmail());
        assertEquals(204, post(PORTAL + "/auth/logout", "{\"refreshToken\":\"" + s.str("refreshToken") + "\"}",
                s.str("accessToken")).status());
        assertEquals(401, post(PORTAL + "/auth/refresh", "{\"refreshToken\":\"" + s.str("refreshToken") + "\"}")
                .status());
    }
}
