package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Phones push queued changes and pull everyone else's; retries, conflicts, permissions and isolation. */
class SyncIT extends CatalogueTestSupport {

    private static String supplierOp(String opId, String id, Long base, String name) {
        return """
                {"opId":"%s","entityType":"SUPPLIER","entityId":"%s","action":"UPSERT","baseVersion":%s,
                 "supplier":{"name":"%s","city":"Pune"}}""".formatted(opId, id, base, name);
    }

    private Resp push(String token, String... ops) {
        return post("/api/v1/sync/push", "{\"ops\":[" + String.join(",", ops) + "]}", token);
    }

    private static JsonNode result(Resp r, int i) {
        return r.body().get("results").get(i);
    }

    private static boolean hasName(JsonNode array, String name) {
        for (JsonNode n : array) if (name.equals(text(n.get("name")))) return true;
        return false;
    }

    @Test
    void retriedPushDoesNotCreateDuplicates() {
        Shop s = shop();
        String op = newId(), id = newId(), name = unique("Retry Traders ");
        Resp first = push(s.owner(), supplierOp(op, id, null, name));
        assertEquals("APPLIED", text(result(first, 0).get("status")), first.raw());
        Resp again = push(s.owner(), supplierOp(op, id, null, name));
        assertEquals("APPLIED", text(result(again, 0).get("status")), "replay returns the stored result");
        Resp third = push(s.owner(), supplierOp(op, id, null, name));
        assertEquals("APPLIED", text(result(third, 0).get("status")));
        Integer rows = jdbc.queryForObject("select count(*) from supplier where name = ?", Integer.class, name);
        assertEquals(1, rows, "pushed 3 times, stored once");
    }

    @Test
    void changesOnOnePhoneReachTheOthers() {
        Shop s = shop();
        long cursor = Long.parseLong(get("/api/v1/sync/pull?since=0", s.staff()).str("nextSince"));

        String id = newId(), name = unique("Owner Added ");
        push(s.owner(), supplierOp(newId(), id, null, name));
        Resp pulled = get("/api/v1/sync/pull?since=" + cursor, s.staff());
        assertTrue(hasName(pulled.body().get("suppliers"), name), "staff phone receives the owner's supplier");
        long next = Long.parseLong(pulled.str("nextSince"));
        assertTrue(next > cursor);
        assertEquals(0, get("/api/v1/sync/pull?since=" + next, s.staff()).body().get("suppliers").size(),
                "nothing new after the cursor");

        Resp staffEdit = push(s.staff(), supplierOp(newId(), id, 0L, name + " (renamed)"));
        assertEquals("APPLIED", text(result(staffEdit, 0).get("status")), staffEdit.raw());
        assertTrue(hasName(get("/api/v1/sync/pull?since=" + next, s.owner()).body().get("suppliers"),
                name + " (renamed)"), "the owner's phone receives the staff edit");
    }

    @Test
    void staleEditBecomesAConflictWithTheServerCopy() {
        Shop s = shop();
        String id = newId();
        push(s.owner(), supplierOp(newId(), id, null, "Original"));
        assertEquals("APPLIED", text(result(push(s.owner(), supplierOp(newId(), id, 0L, "Owner edit")), 0).get("status")));
        Resp late = push(s.staff(), supplierOp(newId(), id, 0L, "Staff edit made offline"));
        JsonNode r = result(late, 0);
        assertEquals("CONFLICT", text(r.get("status")), late.raw());
        assertEquals("Owner edit", text(r.get("supplier").get("name")), "server copy returned for review");
        assertEquals("1", text(r.get("version")));
        Resp resolved = push(s.staff(), supplierOp(newId(), id, 1L, "Staff edit made offline"));
        assertEquals("APPLIED", text(result(resolved, 0).get("status")), "re-sent on top of the server version");
    }

    @Test
    void deletionsPropagate() {
        Shop s = shop();
        String id = newId(), name = unique("To delete ");
        push(s.owner(), supplierOp(newId(), id, null, name));
        long cursor = Long.parseLong(get("/api/v1/sync/pull?since=0", s.staff()).str("nextSince"));
        Resp del = push(s.owner(), """
                {"opId":"%s","entityType":"SUPPLIER","entityId":"%s","action":"DELETE","baseVersion":0}"""
                .formatted(newId(), id));
        assertEquals("APPLIED", text(result(del, 0).get("status")), del.raw());
        JsonNode sups = get("/api/v1/sync/pull?since=" + cursor, s.staff()).body().get("suppliers");
        assertEquals(1, sups.size());
        assertEquals("true", text(sups.get(0).get("deleted")));
    }

    @Test
    void listsAndItemsSyncWithServerCalculatedTotals() {
        Shop s = shop();
        String itemId = newId(), listId = newId();
        Resp r = push(s.staff(),
                """
                {"opId":"%s","entityType":"ITEM","entityId":"%s","action":"UPSERT","baseVersion":null,
                 "item":{"name":"%s","defaultUnitId":"%s","latestPurchasePrice":"140"}}"""
                        .formatted(newId(), itemId, unique("Oil "), LITRE),
                """
                {"opId":"%s","entityType":"STOCK_LIST","entityId":"%s","action":"UPSERT","baseVersion":null,
                 "stockList":%s}""".formatted(newId(), listId, listJson("Synced list", "SAVED", null,
                        line(newId(), "Oil", "20", LITRE, "PER_UNIT", "140"),
                        line(newId(), "Rice", "1.5", KG, "TOTAL", "270"))));
        assertEquals("APPLIED", text(result(r, 0).get("status")), r.raw());
        assertEquals("APPLIED", text(result(r, 1).get("status")), r.raw());
        assertEquals("3070.00", text(result(r, 1).get("stockList").get("totalAmount")));
        JsonNode lists = get("/api/v1/sync/pull?since=0", s.owner()).body().get("stockLists");
        boolean found = false;
        for (JsonNode l : lists) {
            if (listId.equals(text(l.get("id")))) {
                found = true;
                assertEquals(2, l.get("lines").size());
                assertEquals("3070.00", text(l.get("totalAmount")));
            }
        }
        assertTrue(found);
    }

    @Test
    void pushIsCheckedLikeTheApi() {
        Shop s = shop();
        Resp noPermission = push(s.viewer(), supplierOp(newId(), newId(), null, "Nope"));
        assertEquals("REJECTED", text(result(noPermission, 0).get("status")));
        assertEquals("PERMISSION_DENIED", text(result(noPermission, 0).get("code")));
        Resp invalid = push(s.owner(), supplierOp(newId(), newId(), null, ""));
        assertEquals("REJECTED", text(result(invalid, 0).get("status")));
        assertEquals("VALIDATION_FAILED", text(result(invalid, 0).get("code")));
        Resp noPayload = push(s.owner(), """
                {"opId":"%s","entityType":"ITEM","entityId":"%s","action":"UPSERT"}""".formatted(newId(), newId()));
        assertEquals("REJECTED", text(result(noPayload, 0).get("status")));
    }

    @Test
    void pullOnlyContainsWhatTheUserMaySee() {
        Shop s = shop();
        push(s.owner(), supplierOp(newId(), newId(), null, unique("Secret supplier ")));
        post("/api/v1/stock-lists", listJson("Owner list", "DRAFT", null), s.owner());
        post("/api/v1/items", itemJson(unique("Visible item "), PCS, null, null, null), s.owner());
        Resp viewer = get("/api/v1/sync/pull?since=0", s.viewer());
        assertEquals(0, viewer.body().get("suppliers").size(), "no SUPPLIER_VIEW");
        assertEquals(0, viewer.body().get("stockLists").size(), "no list permission");
        assertTrue(viewer.body().get("items").size() >= 1, "ITEM_VIEW");
    }

    @Test
    void pagingDeliversEverythingExactlyOnce() {
        Shop s = shop();
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            String name = unique("Paged " + i + " ");
            expected.add(name);
            push(s.owner(), supplierOp(newId(), newId(), null, name));
            post("/api/v1/items", itemJson(unique("Paged item " + i + " "), PCS, null, null, null), s.owner());
        }
        Set<String> seen = new HashSet<>();
        long since = 0;
        int pages = 0;
        boolean more = true;
        while (more && pages < 20) {
            Resp page = get("/api/v1/sync/pull?limit=3&since=" + since, s.owner());
            for (JsonNode n : page.body().get("suppliers")) {
                assertTrue(seen.add(text(n.get("name"))), "no supplier delivered twice");
            }
            since = Long.parseLong(page.str("nextSince"));
            more = "true".equals(page.str("hasMore"));
            pages++;
        }
        assertEquals(expected, seen);
        assertTrue(pages > 2, "it really paged");
    }

    @Test
    void anotherBusinessCannotSyncIntoOrOutOfThisOne() {
        Shop a = shop();
        Shop b = shop();
        String id = newId(), name = unique("A only ");
        push(a.owner(), supplierOp(newId(), id, null, name));
        assertFalse(hasName(get("/api/v1/sync/pull?since=0", b.owner()).body().get("suppliers"), name));
        Resp hijack = push(b.owner(), supplierOp(newId(), id, 0L, "Taken over"));
        assertEquals("REJECTED", text(result(hijack, 0).get("status")));
        Resp create = push(b.owner(), supplierOp(newId(), id, null, "Same id"));
        assertEquals("REJECTED", text(result(create, 0).get("status")), "an id used by another business");
        assertEquals(name, jdbc.queryForObject("select name from supplier where id = uuid_to_bin(?)", String.class, id));
    }
}
