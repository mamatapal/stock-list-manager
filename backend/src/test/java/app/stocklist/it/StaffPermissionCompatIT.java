package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Staff permission replacement from apps that do not know every permission. Released Stock List Manager apps know
 * only the first 25 codes and save the full list they show; assigned codes they cannot show must survive that save,
 * while everything they can show is still replaced as before.
 */
class StaffPermissionCompatIT extends CatalogueTestSupport {
    private static final String NEW_CODES = "\"SUPPLIER_NETWORK_MANAGE\",\"PURCHASE_ORDER_MANAGE\"";

    private static List<String> codes(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(text(n)));
        return list;
    }

    private record Staff(String id, String username, String temporaryPassword) {}

    private Staff staff(String owner, String permissionsJson) {
        String u = unique("cmp");
        Resp c = createStaff(owner, stepUp(owner, PW), u, permissionsJson);
        assertEquals(201, c.status(), c.raw());
        return new Staff(c.at("/staff/id").toString().replace("\"", ""), u, c.str("temporaryPassword"));
    }

    private Resp save(String token, String password, Staff s, String body) {
        return put("/api/v1/staff/" + s.id() + "/permissions", body, token, stepUp(token, password));
    }

    private List<String> stored(String owner, Staff s) {
        return codes(get("/api/v1/staff/" + s.id() + "/permissions", owner).body().get("permissions"));
    }

    private String allCodes(String token) {
        List<String> all = new ArrayList<>();
        for (JsonNode p : get("/api/v1/permissions", token).body()) all.add("\"" + text(p.get("code")) + "\"");
        return String.join(",", all);
    }

    @Test
    void anOlderAppSavingStaffKeepsThePermissionsItCannotShow() {
        String owner = shop().owner();
        Staff s = staff(owner, "[\"ITEM_VIEW\",\"PURCHASE_RECORD\"," + NEW_CODES + "]");

        // The older app shows ITEM_VIEW and PURCHASE_RECORD, adds ITEM_ADD and saves what it shows.
        Resp r = save(owner, PW, s, "{\"permissions\":[\"ITEM_VIEW\",\"PURCHASE_RECORD\",\"ITEM_ADD\"]}");
        assertEquals(200, r.status(), r.raw());
        assertEquals(List.of("ITEM_ADD", "ITEM_VIEW", "PURCHASE_ORDER_MANAGE", "PURCHASE_RECORD",
                "SUPPLIER_NETWORK_MANAGE"), codes(r.body().get("permissions")));
        assertEquals(codes(r.body().get("permissions")), stored(owner, s));

        // Removing a permission the older app does understand still removes it; the new ones stay.
        Resp removed = save(owner, PW, s, "{\"permissions\":[\"ITEM_ADD\"]}");
        assertEquals(200, removed.status(), removed.raw());
        assertEquals(List.of("ITEM_ADD", "PURCHASE_ORDER_MANAGE", "SUPPLIER_NETWORK_MANAGE"), stored(owner, s));
        Resp emptied = save(owner, PW, s, "{\"permissions\":[]}");
        assertEquals(200, emptied.status(), emptied.raw());
        assertEquals(List.of("PURCHASE_ORDER_MANAGE", "SUPPLIER_NETWORK_MANAGE"), stored(owner, s));

        // The staff member really keeps them.
        String token = staffSession(s.username(), s.temporaryPassword()).str("accessToken");
        String perms = get("/api/v1/auth/me", token).body().get("permissions").toString();
        assertTrue(perms.contains("SUPPLIER_NETWORK_MANAGE") && perms.contains("PURCHASE_ORDER_MANAGE"), perms);
        assertFalse(perms.contains("ITEM_ADD"), perms);
    }

    @Test
    void anAppThatKnowsTheNewPermissionsCanGrantAndRemoveThem() {
        String owner = shop().owner();
        Staff s = staff(owner, "[\"ITEM_VIEW\"]");

        // Asking for a code shows the client knows it: granted as usual.
        Resp granted = save(owner, PW, s, "{\"permissions\":[\"ITEM_VIEW\",\"SUPPLIER_NETWORK_MANAGE\"]}");
        assertEquals(200, granted.status(), granted.raw());
        assertEquals(List.of("ITEM_VIEW", "SUPPLIER_NETWORK_MANAGE"), stored(owner, s));

        // A newer app says which codes it shows, so leaving one out removes it.
        Resp removed = save(owner, PW, s, "{\"permissions\":[\"ITEM_VIEW\"],\"knownPermissions\":[" + allCodes(owner)
                + "]}");
        assertEquals(200, removed.status(), removed.raw());
        assertEquals(List.of("ITEM_VIEW"), stored(owner, s));

        // Codes this server does not have are ignored in knownPermissions but refused as permissions.
        assertEquals(200, save(owner, PW, s, "{\"permissions\":[\"ITEM_VIEW\"],\"knownPermissions\":"
                + "[\"ITEM_VIEW\",\"SOMETHING_FROM_THE_FUTURE\"]}").status());
        Resp unknown = save(owner, PW, s, "{\"permissions\":[\"SOMETHING_FROM_THE_FUTURE\"]}");
        assertEquals(400, unknown.status(), unknown.raw());
        assertEquals("UNKNOWN_PERMISSION", unknown.str("code"));
        assertEquals(List.of("ITEM_VIEW"), stored(owner, s));
    }

    @Test
    void aStaffManagerOnAnOlderAppCanEditStaffWhoHoldPermissionsTheManagerLacks() {
        String owner = shop().owner();
        Staff manager = staff(owner, "[\"STAFF_MANAGE\",\"ITEM_VIEW\",\"ITEM_ADD\"]");
        Staff s = staff(owner, "[\"ITEM_VIEW\"," + NEW_CODES + "]");
        String managerToken = staffSession(manager.username(), manager.temporaryPassword()).str("accessToken");

        // Before: the old app's save would have meant removing codes the manager does not hold (refused, 403).
        Resp edited = save(managerToken, STAFF_PW, s, "{\"permissions\":[\"ITEM_VIEW\",\"ITEM_ADD\"]}");
        assertEquals(200, edited.status(), edited.raw());
        assertEquals(List.of("ITEM_ADD", "ITEM_VIEW", "PURCHASE_ORDER_MANAGE", "SUPPLIER_NETWORK_MANAGE"),
                stored(owner, s));

        // The usual escalation rules still apply to real changes of codes the manager does not hold.
        Resp remove = save(managerToken, STAFF_PW, s, "{\"permissions\":[\"ITEM_VIEW\"],\"knownPermissions\":["
                + allCodes(owner) + "]}");
        assertEquals(403, remove.status(), remove.raw());
        assertEquals("CANNOT_GRANT", remove.str("code"));
        Staff plain = staff(owner, "[\"ITEM_VIEW\"]");
        Resp grant = save(managerToken, STAFF_PW, plain, "{\"permissions\":[\"ITEM_VIEW\",\"PURCHASE_ORDER_MANAGE\"]}");
        assertEquals(403, grant.status(), grant.raw());
        assertEquals("CANNOT_GRANT", grant.str("code"));
        assertEquals(List.of("ITEM_VIEW"), stored(owner, plain));
        assertEquals(List.of("ITEM_ADD", "ITEM_VIEW", "PURCHASE_ORDER_MANAGE", "SUPPLIER_NETWORK_MANAGE"),
                stored(owner, s));
    }

    @Test
    void ownersAndNewStaffKeepTheirPermissionBehaviour() {
        Shop shop = shop();
        String owner = shop.owner();
        JsonNode me = get("/api/v1/auth/me", owner).body().get("permissions");
        assertEquals(27, me.size(), me.toString());
        // New staff created by an older app (no list: defaults) get neither new permission.
        Staff s = staff(owner, null);
        List<String> stored = stored(owner, s);
        assertFalse(stored.contains("SUPPLIER_NETWORK_MANAGE") || stored.contains("PURCHASE_ORDER_MANAGE"),
                stored.toString());
        // A save from an older app grants nothing it did not ask for.
        Resp r = save(owner, PW, s, "{\"permissions\":[\"ITEM_VIEW\"]}");
        assertEquals(List.of("ITEM_VIEW"), codes(r.body().get("permissions")));
        assertEquals(List.of("ITEM_VIEW"), stored(owner, s));
        // Staff without STAFF_MANAGE still cannot change anyone's permissions.
        Resp self = put("/api/v1/staff/" + s.id() + "/permissions", "{\"permissions\":[\"ITEM_VIEW\"]}",
                shop.staff(), null);
        assertTrue(self.status() == 401 || self.status() == 403, self.raw());
    }
}
