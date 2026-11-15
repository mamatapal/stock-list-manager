package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.stocklist.permission.Permission;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Business A must never see or change business B's data, whatever ids it sends. */
class BusinessIsolationIT extends IntegrationTestBase {
    private static final String PW = "Green-Mango-42";

    @Test
    void oneBusinessCannotReachAnothersStaff() {
        String ownerA = unique("a");
        String tokenA = registerBusiness(ownerA, PW).str("accessToken");
        String ownerB = unique("b");
        String tokenB = registerBusiness(ownerB, PW).str("accessToken");

        String staffB = unique("bst");
        Resp createdB = createStaff(tokenB, stepUp(tokenB, PW), staffB, null);
        String staffBId = createdB.at("/staff/id").toString().replace("\"", "");
        String stepA = stepUp(tokenA, PW);

        assertEquals(404, get("/api/v1/staff/" + staffBId, tokenA).status());
        assertEquals(404, get("/api/v1/staff/" + staffBId + "/permissions", tokenA).status());
        assertEquals(404, get("/api/v1/staff/" + staffBId + "/sessions", tokenA).status());
        assertEquals(404, put("/api/v1/staff/" + staffBId + "/permissions", "{\"permissions\":[\"STAFF_MANAGE\"]}",
                tokenA, stepA).status());
        assertEquals(404, put("/api/v1/staff/" + staffBId, "{\"fullName\":\"Taken over\"}", tokenA, stepA).status());
        assertEquals(404, post("/api/v1/staff/" + staffBId + "/deactivate", null, tokenA, stepA).status());
        assertEquals(404, post("/api/v1/staff/" + staffBId + "/reset-password", null, tokenA, stepA).status());
        assertEquals(404, delete("/api/v1/staff/" + staffBId + "/sessions", tokenA, stepA).status());

        Resp listA = get("/api/v1/staff", tokenA);
        assertEquals(200, listA.status());
        for (JsonNode s : listA.body()) {
            assertFalse(staffB.equals(text(s.get("username"))));
        }
        assertEquals("ACTIVE", jdbc.queryForObject("select status from app_user where username = ?", String.class,
                staffB), "B's staff member is untouched");

        Resp activityA = get("/api/v1/activity?size=200", tokenA);
        for (JsonNode item : activityA.body().get("items")) {
            assertFalse(ownerB.equals(text(item.get("username"))), "A's activity log never shows B's events");
        }
        assertEquals(ownerA + " Stores", get("/api/v1/business", tokenA).str("name"));
        assertEquals(ownerB + " Stores", get("/api/v1/business", tokenB).str("name"));
    }

    @Test
    void stepUpTokenFromOneUserCannotBeUsedByAnother() {
        String tokenA = registerBusiness(unique("a"), PW).str("accessToken");
        String tokenB = registerBusiness(unique("b"), PW).str("accessToken");
        String stepB = stepUp(tokenB, PW);
        Resp r = createStaff(tokenA, stepB, unique("st"), null);
        assertEquals(403, r.status());
        assertEquals("STEP_UP_REQUIRED", r.str("code"));
    }

    @Test
    void databasePermissionCatalogueMatchesTheCode() {
        List<String> db = jdbc.queryForList("select code from permission order by sort_order", String.class);
        List<String> code = Arrays.stream(Permission.values()).map(Enum::name).toList();
        assertEquals(code, db);
        assertTrue(db.size() == 25);
    }
}
