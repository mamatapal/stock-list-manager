package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** One owner and two staff, each on their own phone, signing in with their own credentials. */
class MultiDeviceLoginIT extends IntegrationTestBase {
    private static final String PW = "Green-Mango-42";

    private static List<String> codes(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(text(n)));
        return list;
    }

    @Test
    void ownerAndTwoStaffOnThreePhones() {
        String owner = unique("own");
        String phoneA = deviceId(), phoneB = deviceId(), phoneC = deviceId(), phoneA2 = deviceId();
        registerBusiness(owner, PW);
        Resp ownerA = loginAs("OWNER", owner, PW, phoneA, "Owner phone");
        assertEquals(200, ownerA.status(), ownerA.raw());
        String ownerToken = ownerA.str("accessToken");

        String s1 = unique("st1");
        Resp c1 = createStaff(ownerToken, stepUp(ownerToken, PW), s1, null);
        String s2 = unique("st2");
        Resp c2 = createStaff(ownerToken, stepUp(ownerToken, PW), s2,
                "[\"ITEM_VIEW\",\"SUPPLIER_VIEW\",\"EXPIRY_VIEW\"]");
        String s2Id = text(c2.at("/staff/id"));

        // Each staff member on their own phone, with their own password.
        Resp b = loginAs("STAFF", s1, c1.str("temporaryPassword"), phoneB, "Staff 1 phone");
        Resp c = loginAs("STAFF", s2, c2.str("temporaryPassword"), phoneC, "Staff 2 phone");
        assertEquals(200, b.status(), b.raw());
        assertEquals(200, c.status(), c.raw());
        String tokenB = post("/api/v1/auth/password/change", "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}"
                .formatted(c1.str("temporaryPassword"), STAFF_PW), b.str("accessToken")).str("accessToken");
        String tokenC = post("/api/v1/auth/password/change", "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}"
                .formatted(c2.str("temporaryPassword"), STAFF_PW), c.str("accessToken")).str("accessToken");

        // Same business, own identity and own permissions on every phone.
        Resp meA = get("/api/v1/auth/me", ownerToken);
        Resp meB = get("/api/v1/auth/me", tokenB);
        Resp meC = get("/api/v1/auth/me", tokenC);
        assertEquals(meA.str("businessId"), meB.str("businessId"));
        assertEquals(meA.str("businessId"), meC.str("businessId"));
        assertEquals(s1, meB.str("username"));
        assertEquals(s2, meC.str("username"));
        assertEquals(27, meA.body().get("permissions").size());
        assertTrue(codes(meB.body().get("permissions")).contains("PURCHASE_RECORD"));
        assertEquals(List.of("EXPIRY_VIEW", "ITEM_VIEW", "SUPPLIER_VIEW"), codes(meC.body().get("permissions")));

        // The owner can also use a second phone at the same time.
        Resp ownerA2 = loginAs("OWNER", owner, PW, phoneA2, "Owner tablet");
        assertEquals(200, get("/api/v1/auth/me", ownerA2.str("accessToken")).status());
        assertEquals(200, get("/api/v1/auth/me", ownerToken).status(), "first phone still signed in");

        // Deactivating staff 2 affects only phone C.
        assertEquals(200, post("/api/v1/staff/" + s2Id + "/deactivate", null, ownerToken,
                stepUp(ownerToken, PW)).status());
        assertEquals(401, get("/api/v1/auth/me", tokenC).status());
        assertEquals(200, get("/api/v1/auth/me", tokenB).status());
        assertEquals(200, get("/api/v1/auth/me", ownerToken).status());
        Resp again = loginAs("STAFF", s2, STAFF_PW, phoneC, "Staff 2 phone");
        assertEquals(403, again.status());
        assertEquals("ACCOUNT_INACTIVE", again.str("code"));
    }

    @Test
    void ownerAndStaffSignInScreensRefuseTheOtherRole() {
        String owner = unique("own");
        String ownerToken = registerBusiness(owner, PW).str("accessToken");
        String staff = unique("st");
        Resp created = createStaff(ownerToken, stepUp(ownerToken, PW), staff, null);
        staffSession(staff, created.str("temporaryPassword"));

        Resp ownerOnStaffScreen = loginAs("STAFF", owner, PW, deviceId(), "Phone");
        assertEquals(403, ownerOnStaffScreen.status());
        assertEquals("ROLE_MISMATCH", ownerOnStaffScreen.str("code"));
        Resp staffOnOwnerScreen = loginAs("OWNER", staff, STAFF_PW, deviceId(), "Phone");
        assertEquals(403, staffOnOwnerScreen.status());
        assertEquals("ROLE_MISMATCH", staffOnOwnerScreen.str("code"));

        Resp wrongPassword = loginAs("STAFF", owner, "Wrong-Password-1", deviceId(), "Phone");
        assertEquals(401, wrongPassword.status(), "the role is never revealed without the right password");
        assertEquals("INVALID_CREDENTIALS", wrongPassword.str("code"));
        assertFalse(wrongPassword.raw().contains("owner"));
    }
}
