package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * System-wide checks driven by the OpenAPI document, so every endpoint (including ones added later) is covered:
 * <ul>
 *   <li>a staff member with no permissions is refused (403) everywhere except the few self-service endpoints;</li>
 *   <li>another business's owner gets 404 for every record of this business, through the API and through sync.</li>
 * </ul>
 * Requests use real ids, valid bodies, the right If-Match version and a valid step-up token, so a refusal comes from
 * the permission or tenant check and not from a malformed request.
 */
class SystemSecurityIT extends PurchaseTestSupport {
    /** Public, or harmful to the test session itself (logout, password change), or covered elsewhere. */
    private static final Set<String> SKIP = Set.of(
            "POST /api/v1/auth/register-business", "POST /api/v1/auth/login", "POST /api/v1/auth/refresh",
            "POST /api/v1/auth/password/forgot", "POST /api/v1/auth/password/reset", "POST /api/v1/auth/logout",
            "POST /api/v1/auth/password/change", "POST /api/v1/auth/reauth", "POST /api/v1/sync/push");

    /** Every signed-in user of the business may call these (sync pull returns only what they may see). */
    private static final Set<String> SELF_SERVICE = Set.of(
            "GET /api/v1/auth/me", "GET /api/v1/business", "GET /api/v1/permissions", "GET /api/v1/units",
            "GET /api/v1/people", "GET /api/v1/sync/pull");

    record World(Shop shop, String ownerToken, String sup, String item, String unit, String list, String purchase,
                 String purchaseLine, String draft, String batch, String staffId) {}

    private World world() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Agarwal");
        String item = item(s.owner(), "Rice", KG, "40", "50");
        String unit = post("/api/v1/units", "{\"code\":\"" + unique("tray") + "\",\"name\":\"Tray\"}", s.owner()).str("id");
        Resp list = post("/api/v1/stock-lists", listJson("Monday", "SAVED", sup, line(newId(), "Rice", "5", KG, "PER_UNIT", "40")), s.owner());
        assertEquals(201, list.status(), list.raw());
        String line = newId(), receipt = newId();
        Resp p = post("/api/v1/purchases", purchaseJson(sup, "A-1", "2026-11-01", true,
                delivery(newId(), null, "2026-11-02", receipt(receipt, line, "4", "B-1", "2027-01-31")),
                pline(line, item, "10", KG, "PER_UNIT", "40", "50")), s.owner());
        assertEquals(201, p.status(), p.raw());
        Resp draft = post("/api/v1/purchases", purchaseJson(sup, "A-draft", "2026-11-03", false, null,
                pline(newId(), item, "1", KG, "PER_UNIT", "40", null)), s.owner());
        assertEquals(201, draft.status(), draft.raw());
        Resp staff = createStaff(s.owner(), stepUp(s.owner(), PW), unique("st"), null);
        String staffId = staff.at("/staff/id").toString().replace("\"", "");
        return new World(s, s.owner(), sup, item, unit, list.str("id"), p.str("id"), line, draft.str("id"), receipt, staffId);
    }

    private List<String[]> endpoints() {
        Resp docs = get("/v3/api-docs", null);
        assertEquals(200, docs.status());
        List<String[]> list = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : docs.body().get("paths").properties()) {
            // Supplier Management and Supplier Administration endpoints do not accept shop users at all; they are
            // covered by SupplierPortalIT and SupplierAdministrationIT.
            if (path.getKey().startsWith("/api/v1/supplier-portal/") || path.getKey().startsWith("/api/v1/admin/")) {
                continue;
            }
            for (Map.Entry<String, JsonNode> op : path.getValue().properties()) {
                list.add(new String[]{op.getKey().toUpperCase(), path.getKey()});
            }
        }
        return list;
    }

    private long versionOf(String path, String token) {
        Resp r = get(path, token);
        assertEquals(200, r.status(), path + " " + r.raw());
        return Long.parseLong(text(r.body().get("version")));
    }

    /** A valid request for this endpoint against the world's records. */
    private record Call(String path, String body, Map<String, String> headers) {}

    private Call request(String method, String template, World w, String stepUp) {
        String path = template
                .replace("/purchases/by-supplier/{supplierId}", "/purchases/by-supplier/" + w.sup())
                .replace("/purchases/by-item/{itemId}", "/purchases/by-item/" + w.item());
        String id = null, versionPath = null;
        if (template.startsWith("/api/v1/suppliers/{id}")) { id = w.sup(); versionPath = "/api/v1/suppliers/" + id; }
        else if (template.startsWith("/api/v1/items/{id}")) { id = w.item(); versionPath = "/api/v1/items/" + id; }
        else if (template.startsWith("/api/v1/units/{id}")) id = w.unit();
        else if (template.startsWith("/api/v1/stock-lists/{id}")) { id = w.list(); versionPath = "/api/v1/stock-lists/" + id; }
        else if (template.startsWith("/api/v1/purchases/{id}")) {
            id = method.equals("DELETE") ? w.draft() : w.purchase();
            versionPath = "/api/v1/purchases/" + id;
        }
        else if (template.startsWith("/api/v1/batches/{id}")) id = w.batch();
        else if (template.startsWith("/api/v1/staff/{id}")) id = w.staffId();
        if (id != null) path = path.replace("{id}", id);
        if (path.startsWith("/api/v1/reports/")) path += "?from=2026-01-01&to=2026-12-31";

        String body = switch (method + " " + template) {
            case "POST /api/v1/suppliers", "PUT /api/v1/suppliers/{id}" -> supplierJson("Changed " + unique(""));
            case "POST /api/v1/items", "PUT /api/v1/items/{id}" -> itemJson("Item " + unique(""), KG, "1", "2", null);
            case "POST /api/v1/units" -> "{\"code\":\"u" + unique("").substring(0, 5) + "\",\"name\":\"Unit\"}";
            case "POST /api/v1/stock-lists", "PUT /api/v1/stock-lists/{id}" ->
                    listJson("Taken", "SAVED", null, line(newId(), "Salt", "1", KG, "PER_UNIT", "1"));
            case "POST /api/v1/purchases", "PUT /api/v1/purchases/{id}" ->
                    purchaseJson(w.sup(), "X-" + unique(""), "2026-11-05", false, null, pline(newId(), w.item(), "1", KG, "PER_UNIT", "1", null));
            case "POST /api/v1/purchases/{id}/cancel" -> "{\"reason\":\"Not ours\"}";
            case "POST /api/v1/purchases/{id}/deliveries" ->
                    delivery(newId(), null, "2026-11-05", receipt(newId(), w.purchaseLine(), "1", null, null));
            case "POST /api/v1/staff" -> "{\"fullName\":\"Intruder\",\"username\":\"" + unique("in") + "\"}";
            case "PUT /api/v1/staff/{id}" -> "{\"fullName\":\"Renamed\"}";
            case "PUT /api/v1/staff/{id}/permissions" -> "{\"permissions\":[\"STAFF_MANAGE\",\"EXPECTED_PROFIT_VIEW\"]}";
            case "PUT /api/v1/business" -> """
                    {"name":"Taken over","address":"x","city":"x","state":"x","pinCode":"411001","phone":"9876543210",
                     "email":"x@example.com"}""";
            default -> method.equals("GET") || method.equals("DELETE") ? null : "{}";
        };
        Map<String, String> headers = new HashMap<>();
        if (stepUp != null) headers.put("X-Step-Up-Token", stepUp);
        if (versionPath != null && (method.equals("PUT") || method.equals("DELETE") || template.endsWith("/cancel"))) {
            headers.put("If-Match", Long.toString(versionOf(versionPath, w.ownerToken())));
        }
        // Units have no single-record GET; the permission and business checks come before the version check anyway.
        if (template.startsWith("/api/v1/units/{id}") && method.equals("DELETE")) headers.put("If-Match", "1");
        return new Call(path, body, headers);
    }

    @Test
    void aStaffMemberWithNoPermissionsIsRefusedOnEveryEndpoint() {
        World w = world();
        String u = unique("nobody");
        Resp c = createStaff(w.ownerToken(), stepUp(w.ownerToken(), PW), u, "[]");
        assertEquals(201, c.status(), c.raw());
        String token = staffSession(u, c.str("temporaryPassword")).str("accessToken");
        String step = stepUp(token, STAFF_PW);

        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (String[] ep : endpoints()) {
            String key = ep[0] + " " + ep[1];
            if (SKIP.contains(key)) continue;
            Call call = request(ep[0], ep[1], w, step);
            int status = callWithHeaders(ep[0], call.path(), call.body(), token, call.headers()).status();
            int expected = SELF_SERVICE.contains(key) ? 200 : 403;
            if (status != expected) wrong.add(key + " -> " + status + " (expected " + expected + ")");
            checked++;
        }
        assertTrue(checked >= 50, "checked " + checked);
        assertEquals(List.of(), wrong, "endpoints that did not refuse a staff member without permissions");

        // Sync is the other way in: everything this person pushes is refused, and pull gives them no records.
        String push = String.join(",",
                op("SUPPLIER", w.sup(), versionOf("/api/v1/suppliers/" + w.sup(), w.ownerToken()), "supplier", supplierJson("Hacked")),
                op("SUPPLIER", newId(), null, "supplier", supplierJson("New")),
                op("ITEM", w.item(), versionOf("/api/v1/items/" + w.item(), w.ownerToken()), "item", itemJson("Hacked", KG, "1", "2", null)),
                op("STOCK_LIST", newId(), null, "stockList", listJson("New", "SAVED", null)),
                op("PURCHASE", newId(), null, "purchase", purchaseJson(w.sup(), "S-1", "2026-11-05", true, null,
                        pline(newId(), w.item(), "1", KG, "PER_UNIT", "1", null))),
                deliveryOp(w.purchase(), w.purchaseLine()));
        Resp r = post("/api/v1/sync/push", "{\"ops\":[" + push + "]}", token);
        assertEquals(200, r.status(), r.raw());
        for (JsonNode res : r.body().get("results")) {
            assertEquals("REJECTED", text(res.get("status")), res.toString());
        }
        assertEquals("Agarwal", get("/api/v1/suppliers/" + w.sup(), w.ownerToken()).str("name"), "nothing changed");
        Resp pull = get("/api/v1/sync/pull?since=0", token);
        for (String kind : List.of("suppliers", "items", "stockLists", "purchases", "batches")) {
            assertEquals(0, pull.body().get(kind).size(), kind + " reached a user who may not see them");
        }
    }

    @Test
    void anotherBusinessGets404ForEveryRecordOfThisOne() {
        World a = world();
        Shop b = shop();
        String stepB = stepUp(b.owner(), PW);
        String before = snapshot(a);

        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (String[] ep : endpoints()) {
            if (!ep[1].contains("{")) continue; // endpoints that name a record
            Call call = request(ep[0], ep[1], a, stepB);
            // Use B's own step-up; the If-Match versions are A's real ones.
            int status = callWithHeaders(ep[0], call.path(), call.body(), b.owner(), call.headers()).status();
            if (status != 404) wrong.add(ep[0] + " " + ep[1] + " -> " + status);
            checked++;
        }
        assertTrue(checked >= 25, "checked " + checked);
        assertEquals(List.of(), wrong, "endpoints where another business's owner was not told 'not found'");

        // Lists and searches filtered by A's ids come back empty.
        for (String path : List.of("/api/v1/purchases?supplierId=" + a.sup(), "/api/v1/batches?itemId=" + a.item(),
                "/api/v1/stock-lists?supplierId=" + a.sup(), "/api/v1/suppliers", "/api/v1/items", "/api/v1/purchases",
                "/api/v1/batches?expiry=ALL", "/api/v1/stock-lists")) {
            Resp r = get(path, b.owner());
            assertEquals(200, r.status(), path);
            assertEquals(0, r.body().size(), path + " showed another business's records");
        }
        Resp rep = get("/api/v1/reports/purchases?from=2026-01-01&to=2026-12-31&supplierId=" + a.sup(), b.owner());
        assertEquals("0", rep.str("purchaseCount"));

        // Through sync: B names A's records; nothing of A changes and B's pull never contains A's ids.
        String push = String.join(",",
                op("SUPPLIER", a.sup(), versionOf("/api/v1/suppliers/" + a.sup(), a.ownerToken()), "supplier", supplierJson("Hacked")),
                op("ITEM", a.item(), versionOf("/api/v1/items/" + a.item(), a.ownerToken()), "item", itemJson("Hacked", KG, "1", "2", null)),
                op("STOCK_LIST", a.list(), versionOf("/api/v1/stock-lists/" + a.list(), a.ownerToken()), "stockList", listJson("Hacked", "SAVED", null)),
                op("PURCHASE", a.purchase(), versionOf("/api/v1/purchases/" + a.purchase(), a.ownerToken()), "purchase",
                        purchaseJson(a.sup(), "Hacked", "2026-11-05", true, null, pline(newId(), a.item(), "1", KG, "PER_UNIT", "1", null))),
                deliveryOp(a.purchase(), a.purchaseLine()),
                deleteOp("SUPPLIER", a.sup(), versionOf("/api/v1/suppliers/" + a.sup(), a.ownerToken())));
        Resp r = post("/api/v1/sync/push", "{\"ops\":[" + push + "]}", b.owner());
        assertEquals(200, r.status(), r.raw());
        for (JsonNode res : r.body().get("results")) {
            assertTrue(!"APPLIED".equals(text(res.get("status"))), "applied in another business: " + res);
        }
        String pull = get("/api/v1/sync/pull?since=0", b.owner()).raw();
        for (String id : List.of(a.sup(), a.item(), a.list(), a.purchase(), a.batch(), a.staffId())) {
            assertTrue(!pull.contains(id), "B's pull contains A's " + id);
        }
        assertEquals(before, snapshot(a), "A's records were changed by another business");
    }

    private String snapshot(World w) {
        return String.join("\n", List.of(
                get("/api/v1/suppliers/" + w.sup(), w.ownerToken()).raw(),
                get("/api/v1/items/" + w.item(), w.ownerToken()).raw(),
                get("/api/v1/stock-lists/" + w.list(), w.ownerToken()).raw(),
                get("/api/v1/purchases/" + w.purchase(), w.ownerToken()).raw(),
                get("/api/v1/purchases/" + w.draft(), w.ownerToken()).raw(),
                get("/api/v1/batches/" + w.batch(), w.ownerToken()).raw(),
                get("/api/v1/staff/" + w.staffId(), w.ownerToken()).raw(),
                get("/api/v1/business", w.ownerToken()).raw()));
    }

    private static String op(String type, String id, Long base, String field, String payload) {
        return """
                {"opId":"%s","entityType":"%s","entityId":"%s","action":"UPSERT","baseVersion":%s,"%s":%s}
                """.formatted(newId(), type, id, base, field, payload);
    }

    private static String deliveryOp(String purchaseId, String lineId) {
        String id = newId();
        return op("DELIVERY", id, null, "delivery", delivery(id, purchaseId, "2026-11-05", receipt(newId(), lineId, "1", null, null)));
    }

    private static String deleteOp(String type, String id, long base) {
        return """
                {"opId":"%s","entityType":"%s","entityId":"%s","action":"DELETE","baseVersion":%d}
                """.formatted(newId(), type, id, base);
    }
}
