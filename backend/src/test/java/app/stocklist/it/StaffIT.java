package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class StaffIT extends IntegrationTestBase {
    private static final String PW = "Green-Mango-42";

    private static List<String> codes(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(text(n)));
        return list;
    }

    @Test
    void ownerCreatesStaffWithSafeDefaults() {
        String owner = unique("own");
        String token = registerBusiness(owner, PW).str("accessToken");

        Resp noStepUp = createStaff(token, null, unique("st"), null);
        assertEquals(403, noStepUp.status());
        assertEquals("STEP_UP_REQUIRED", noStepUp.str("code"));

        String staffUser = unique("st");
        Resp created = createStaff(token, stepUp(token, PW), staffUser, null);
        assertEquals(201, created.status(), created.raw());
        String temp = created.str("temporaryPassword");
        assertNotNull(temp);
        List<String> perms = codes(created.body().get("permissions"));
        assertTrue(perms.contains("PURCHASE_RECORD"));
        assertFalse(perms.contains("STAFF_MANAGE"));
        assertFalse(perms.contains("PROFIT_REPORT_VIEW"));

        Resp staffLogin = login(staffUser, temp);
        assertEquals(200, staffLogin.status(), staffLogin.raw());
        assertEquals("true", text(staffLogin.body().get("user").get("mustChangePassword")));
        Resp blocked = get("/api/v1/business", staffLogin.str("accessToken"));
        assertEquals(403, blocked.status(), "a temporary password only allows choosing a new one");
        assertEquals("PASSWORD_CHANGE_REQUIRED", blocked.str("code"));
        assertEquals(200, get("/api/v1/auth/me", staffLogin.str("accessToken")).status());

        Resp own = staffSession(staffUser, temp);
        assertEquals("false", text(own.body().get("user").get("mustChangePassword")));
        assertEquals(200, get("/api/v1/business", own.str("accessToken")).status());
    }

    @Test
    void staffCannotUseOwnerFunctionsEvenThroughTheApi() {
        String owner = unique("own");
        Resp ownerSession = registerBusiness(owner, PW);
        String ownerToken = ownerSession.str("accessToken");
        String staffUser = unique("st");
        String temp = createStaff(ownerToken, stepUp(ownerToken, PW), staffUser, null).str("temporaryPassword");
        String staffToken = staffSession(staffUser, temp).str("accessToken");

        assertEquals(403, get("/api/v1/staff", staffToken).status());
        assertEquals(403, get("/api/v1/activity", staffToken).status());
        String staffStepUp = stepUp(staffToken, STAFF_PW);
        assertEquals(403, put("/api/v1/business", """
                {"name":"Hacked","address":"x","city":"x","state":"x","pinCode":"411001",
                 "phone":"9876543210","email":"x@example.com"}""", staffToken, staffStepUp).status());
        assertEquals(403, createStaff(staffToken, staffStepUp, unique("st"), null).status());
        assertEquals(200, get("/api/v1/business", staffToken).status(), "business details are readable for PDFs");
    }

    @Test
    void deactivationTakesEffectOnTheNextRequest() {
        String owner = unique("own");
        String ownerToken = registerBusiness(owner, PW).str("accessToken");
        String staffUser = unique("st");
        Resp created = createStaff(ownerToken, stepUp(ownerToken, PW), staffUser, null);
        String staffId = created.at("/staff/id").toString().replace("\"", "");
        String temp = created.str("temporaryPassword");
        Resp staffSession = staffSession(staffUser, temp);
        assertEquals(200, get("/api/v1/auth/me", staffSession.str("accessToken")).status());

        Resp deact = post("/api/v1/staff/" + staffId + "/deactivate", null, ownerToken, stepUp(ownerToken, PW));
        assertEquals(200, deact.status(), deact.raw());
        assertEquals("INACTIVE", deact.str("status"));

        assertEquals(401, get("/api/v1/auth/me", staffSession.str("accessToken")).status());
        assertEquals(401, post("/api/v1/auth/refresh",
                "{\"refreshToken\":\"%s\"}".formatted(staffSession.str("refreshToken"))).status(),
                "deactivation revoked every session");
        Resp relog = login(staffUser, STAFF_PW);
        assertEquals(403, relog.status());
        assertEquals("ACCOUNT_INACTIVE", relog.str("code"));

        assertEquals(200, post("/api/v1/staff/" + staffId + "/activate", null, ownerToken,
                stepUp(ownerToken, PW)).status());
        assertEquals(200, login(staffUser, STAFF_PW).status());
    }

    @Test
    void staffManagerCannotEscalate() {
        String owner = unique("own");
        String ownerToken = registerBusiness(owner, PW).str("accessToken");
        String managerUser = unique("mgr");
        Resp manager = createStaff(ownerToken, stepUp(ownerToken, PW), managerUser,
                "[\"STAFF_MANAGE\",\"ITEM_VIEW\",\"ITEM_ADD\"]");
        String managerId = manager.at("/staff/id").toString().replace("\"", "");
        String managerTemp = manager.str("temporaryPassword");
        String managerToken = staffSession(managerUser, managerTemp).str("accessToken");
        String managerStepUp = stepUp(managerToken, STAFF_PW);

        Resp tooMuch = createStaff(managerToken, managerStepUp, unique("st"), "[\"PROFIT_REPORT_VIEW\"]");
        assertEquals(403, tooMuch.status());
        assertEquals("CANNOT_GRANT", tooMuch.str("code"));

        Resp okStaff = createStaff(managerToken, managerStepUp, unique("st"), null);
        assertEquals(201, okStaff.status(), okStaff.raw());
        assertEquals(List.of("ITEM_ADD", "ITEM_VIEW"), codes(okStaff.body().get("permissions")),
                "defaults are limited to what the manager holds");

        Resp self = put("/api/v1/staff/" + managerId + "/permissions",
                "{\"permissions\":[\"STAFF_MANAGE\",\"ITEM_VIEW\",\"ITEM_ADD\",\"SETTINGS_MANAGE\"]}",
                managerToken, managerStepUp);
        assertEquals(403, self.status());
        assertEquals("CANNOT_CHANGE_SELF", self.str("code"));

        String ownerId = jdbc.queryForObject(
                "select bin_to_uuid(id) from app_user where username = ?", String.class, owner);
        assertEquals(404, get("/api/v1/staff/" + ownerId, managerToken).status(), "the owner is not a staff record");
    }

    @Test
    void permissionChangesApplyImmediatelyAndAreAudited() {
        String owner = unique("own");
        String ownerToken = registerBusiness(owner, PW).str("accessToken");
        String staffUser = unique("st");
        Resp created = createStaff(ownerToken, stepUp(ownerToken, PW), staffUser, null);
        String staffId = created.at("/staff/id").toString().replace("\"", "");
        String temp = created.str("temporaryPassword");
        String staffToken = staffSession(staffUser, temp).str("accessToken");
        assertEquals(403, get("/api/v1/staff", staffToken).status());

        Resp set = put("/api/v1/staff/" + staffId + "/permissions", "{\"permissions\":[\"STAFF_MANAGE\"]}",
                ownerToken, stepUp(ownerToken, PW));
        assertEquals(200, set.status(), set.raw());

        assertEquals(401, get("/api/v1/staff", staffToken).status(), "old token is retired after a permission change");
        Resp fresh = login(staffUser, STAFF_PW);
        assertEquals(200, get("/api/v1/staff", fresh.str("accessToken")).status());

        Resp activity = get("/api/v1/activity", ownerToken);
        assertEquals(200, activity.status());
        boolean audited = false;
        for (JsonNode item : activity.body().get("items")) {
            audited |= "PERMISSIONS_CHANGED".equals(text(item.get("action")));
        }
        assertTrue(audited);
    }

    @Test
    void resetStaffPasswordSignsThemOut() {
        String owner = unique("own");
        String ownerToken = registerBusiness(owner, PW).str("accessToken");
        String staffUser = unique("st");
        Resp created = createStaff(ownerToken, stepUp(ownerToken, PW), staffUser, null);
        String staffId = created.at("/staff/id").toString().replace("\"", "");
        Resp staffSession = staffSession(staffUser, created.str("temporaryPassword"));

        Resp reset = post("/api/v1/staff/" + staffId + "/reset-password", null, ownerToken, stepUp(ownerToken, PW));
        assertEquals(200, reset.status(), reset.raw());
        assertEquals(401, get("/api/v1/auth/me", staffSession.str("accessToken")).status());
        assertEquals(401, login(staffUser, STAFF_PW).status(), "the old password no longer works");
        assertEquals(200, login(staffUser, reset.str("temporaryPassword")).status());
    }
}
