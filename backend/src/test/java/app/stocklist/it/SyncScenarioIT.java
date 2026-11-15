package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Multi-device sync, exactly as the phones use it (/sync/push and /sync/pull with their own cursors), including
 * lost responses, retries, two people editing, deletion against edits, concurrent receiving, and accounts that are
 * deactivated or signed out while changes are pending.
 */
class SyncScenarioIT extends PurchaseTestSupport {

    /** One phone: its token and its pull cursor. */
    private final class Phone {
        final String token;
        long cursor;

        Phone(String token) { this.token = token; }

        Resp push(String... ops) {
            Resp r = post("/api/v1/sync/push", "{\"ops\":[" + String.join(",", ops) + "]}", token);
            assertEquals(200, r.status(), r.raw());
            return r;
        }

        JsonNode pull() {
            Resp r = get("/api/v1/sync/pull?since=" + cursor, token);
            assertEquals(200, r.status(), r.raw());
            cursor = Long.parseLong(r.str("nextSince"));
            return r.body();
        }
    }

    private static String op(String opId, String type, String id, Long base, String field, String payload) {
        return """
                {"opId":"%s","entityType":"%s","entityId":"%s","action":"UPSERT","baseVersion":%s,"%s":%s}
                """.formatted(opId, type, id, base, field, payload);
    }

    private static String deleteOp(String opId, String type, String id, long base) {
        return """
                {"opId":"%s","entityType":"%s","entityId":"%s","action":"DELETE","baseVersion":%d}
                """.formatted(opId, type, id, base);
    }

    private static JsonNode result(Resp r, int i) { return r.body().get("results").get(i); }

    private static String status(Resp r, int i) { return text(result(r, i).get("status")); }

    private static JsonNode find(JsonNode array, String id) {
        for (JsonNode n : array) if (id.equals(text(n.get("id")))) return n;
        return null;
    }

    private int count(String table, String column, String id) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = uuid_to_bin(?)",
                Integer.class, id);
    }

    /** The scenario from the requirements, step by step. */
    @Test
    void ownerAndStaffShareDataAcrossPhones() {
        Shop s = shop();
        Phone a = new Phone(s.owner());   // Phone A: owner
        Phone b = new Phone(s.staff());   // Phone B: staff
        a.pull(); b.pull();
        String item = item(s.owner(), "Toor Dal", KG, null, null);
        a.pull(); b.pull();

        // 1. Owner creates a supplier on Phone A.
        String supplier = newId();
        assertEquals("APPLIED", status(a.push(op(newId(), "SUPPLIER", supplier, null, "supplier",
                supplierJson("Phone A Traders"))), 0));

        // 2. Staff sees the supplier on Phone B after sync.
        JsonNode seen = find(b.pull().get("suppliers"), supplier);
        assertEquals("Phone A Traders", text(seen.get("name")));

        // 3. Staff records a purchase on Phone B (with the goods that came with the bill).
        String purchase = newId(), line = newId(), firstDelivery = newId();
        Resp p = b.push(op(newId(), "PURCHASE", purchase, null, "purchase", purchaseJson(supplier, "B-001", "2026-11-14", true,
                delivery(firstDelivery, null, "2026-11-14", receipt(newId(), line, "6", "LOT-1", "2027-06-30")),
                pline(line, item, "10", KG, "PER_UNIT", "120", "140"))));
        assertEquals("APPLIED", status(p, 0), p.raw());

        // 4. Owner sees that purchase on Phone A after sync.
        JsonNode onA = find(a.pull().get("purchases"), purchase);
        assertEquals("B-001", text(onA.get("invoiceNo")));
        assertEquals("PARTIAL", text(onA.get("status")));
        assertEquals("1200.00", text(onA.get("totalCost")));

        // 5. Staff goes offline and records the rest of the goods. The upload reaches the server but the reply is
        //    lost, so Phone B sends the same operation again, twice.
        String offlineDelivery = newId();
        String offlineOp = op(newId(), "DELIVERY", offlineDelivery, null, "delivery",
                delivery(offlineDelivery, purchase, "2026-11-15", receipt(newId(), line, "4", "LOT-2", "2027-07-31")));
        assertEquals("APPLIED", status(b.push(offlineOp), 0));
        assertEquals("APPLIED", status(b.push(offlineOp), 0), "retry returns the stored answer");
        assertEquals("APPLIED", status(b.push(offlineOp), 0));

        // 6. It synced without duplication: exactly two deliveries, ten kilograms, one purchase.
        assertEquals(2, count("purchase_delivery", "purchase_id", purchase));
        assertEquals(1, count("purchase", "id", purchase));
        assertEquals(2, count("batch", "purchase_id", purchase));
        JsonNode after = find(a.pull().get("purchases"), purchase);
        assertEquals("RECEIVED", text(after.get("status")));
        assertEquals(2, after.get("deliveries").size());
        assertEquals("10", text(after.get("lines").get(0).get("receivedQuantity")));
    }

    @Test
    void aWholeBatchSentAgainChangesNothing() {
        Shop s = shop();
        Phone b = new Phone(s.staff());
        String sup = newId(), item = newId(), purchase = newId(), line = newId();
        String[] ops = {
                op(newId(), "SUPPLIER", sup, null, "supplier", supplierJson("Retry Traders")),
                op(newId(), "ITEM", item, null, "item", "{\"name\":\"Retry item\",\"defaultUnitId\":\"" + PCS + "\"}"),
                op(newId(), "PURCHASE", purchase, null, "purchase", purchaseJson(sup, "R-1", "2026-11-14", true, null,
                        pline(line, item, "3", PCS, "PER_UNIT", "10", null))),
        };
        Resp first = b.push(ops);
        Resp again = b.push(ops);
        for (int i = 0; i < 3; i++) {
            assertEquals("APPLIED", status(first, i), first.raw());
            assertEquals("APPLIED", status(again, i));
            assertEquals(text(result(first, i).get("version")), text(result(again, i).get("version")));
        }
        assertEquals(1, count("supplier", "id", sup));
        assertEquals(1, count("item", "id", item));
        assertEquals(1, count("purchase_item", "purchase_id", purchase));
    }

    @Test
    void twoPeopleEditingTheSameRecord() {
        Shop s = shop();
        Phone a = new Phone(s.owner()), b = new Phone(s.staff());
        String sup = newId();
        long v0 = Long.parseLong(text(result(a.push(op(newId(), "SUPPLIER", sup, null, "supplier", supplierJson("Original"))), 0).get("version")));
        b.pull();

        assertEquals("APPLIED", status(a.push(op(newId(), "SUPPLIER", sup, v0, "supplier", supplierJson("Owner's edit"))), 0));
        Resp late = b.push(op(newId(), "SUPPLIER", sup, v0, "supplier", supplierJson("Staff's edit")));
        assertEquals("CONFLICT", status(late, 0), "the newer server data is not overwritten");
        assertEquals("Owner's edit", text(result(late, 0).get("supplier").get("name")));
        assertEquals("Owner's edit", get("/api/v1/suppliers/" + sup, s.owner()).str("name"));

        // Staff chooses "keep mine": sent again on top of the version they have now seen.
        long v1 = Long.parseLong(text(result(late, 0).get("version")));
        assertEquals("APPLIED", status(b.push(op(newId(), "SUPPLIER", sup, v1, "supplier", supplierJson("Staff's edit"))), 0));
        assertEquals("Staff's edit", text(find(a.pull().get("suppliers"), sup).get("name")));
    }

    @Test
    void editAgainstADeletionIsAConflictAndKeepMineRestores() {
        Shop s = shop();
        Phone a = new Phone(s.owner()), b = new Phone(s.staff());
        String sup = newId();
        long v0 = Long.parseLong(text(result(a.push(op(newId(), "SUPPLIER", sup, null, "supplier", supplierJson("Short-lived"))), 0).get("version")));
        b.pull();
        assertEquals("APPLIED", status(a.push(deleteOp(newId(), "SUPPLIER", sup, v0)), 0));

        Resp edit = b.push(op(newId(), "SUPPLIER", sup, v0, "supplier", supplierJson("Still needed")));
        assertEquals("CONFLICT", status(edit, 0));
        assertTrue(result(edit, 0).get("supplier").get("deleted").asBoolean(), "the phone learns it was deleted");

        long v1 = Long.parseLong(text(result(edit, 0).get("version")));
        Resp restore = b.push(op(newId(), "SUPPLIER", sup, v1, "supplier", supplierJson("Still needed")));
        assertEquals("APPLIED", status(restore, 0));
        assertFalse(result(restore, 0).get("supplier").get("deleted").asBoolean());
        JsonNode onA = find(a.pull().get("suppliers"), sup);
        assertFalse(onA.get("deleted").asBoolean());
        assertEquals("Still needed", text(onA.get("name")));
    }

    @Test
    void deletionsReachEveryPhone() {
        Shop s = shop();
        Phone a = new Phone(s.owner()), b = new Phone(s.staff());
        String sup = newId();
        long v0 = Long.parseLong(text(result(a.push(op(newId(), "SUPPLIER", sup, null, "supplier", supplierJson("Gone soon"))), 0).get("version")));
        assertFalse(find(b.pull().get("suppliers"), sup).get("deleted").asBoolean());
        a.push(deleteOp(newId(), "SUPPLIER", sup, v0));
        assertTrue(find(b.pull().get("suppliers"), sup).get("deleted").asBoolean(), "a soft delete is synced, not lost");
        assertEquals(1, count("supplier", "id", sup), "kept in the database");
    }

    @Test
    void twoPhonesReceivingTheSamePurchaseAtOnceKeepBothDeliveries() throws Exception {
        Shop s = shop();
        String sup = supplier(s.owner(), "Busy"), item = item(s.owner(), "Busy item", PCS, null, null);
        String u = unique("st2");
        Resp c = createStaff(s.owner(), stepUp(s.owner(), PW), u, null);
        String staff2 = staffSession(u, c.str("temporaryPassword")).str("accessToken");
        String purchase = newId(), line = newId();
        new Phone(s.owner()).push(op(newId(), "PURCHASE", purchase, null, "purchase", purchaseJson(sup, "BUSY", "2026-11-14", true, null,
                pline(line, item, "100", PCS, "PER_UNIT", "1", null))));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Resp>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String token = i % 2 == 0 ? s.staff() : staff2;
            String d = newId();
            String body = op(newId(), "DELIVERY", d, null, "delivery", delivery(d, purchase, "2026-11-14", receipt(newId(), line, "5", null, null)));
            futures.add(pool.submit(() -> { go.await(); return new Phone(token).push(body); }));
        }
        go.countDown();
        for (Future<Resp> f : futures) {
            assertEquals("APPLIED", status(f.get(), 0), f.get().raw());
        }
        pool.shutdown();
        assertEquals(8, count("purchase_delivery", "purchase_id", purchase));
        JsonNode p = get("/api/v1/purchases/" + purchase, s.owner()).body();
        assertEquals("40", text(p.get("lines").get(0).get("receivedQuantity")));
        assertEquals("PARTIAL", text(p.get("status")));
    }

    @Test
    void deactivatedOrSignedOutPhonesCannotSendChanges() {
        Shop s = shop();
        String u = unique("dx");
        Resp c = createStaff(s.owner(), stepUp(s.owner(), PW), u, null);
        String staffId = c.at("/staff/id").toString().replace("\"", "");
        Resp session = staffSession(u, c.str("temporaryPassword"));
        String staff = session.str("accessToken");
        String sup = newId();
        String pending = op(newId(), "SUPPLIER", sup, null, "supplier", supplierJson("Made while deactivated"));

        assertEquals(200, post("/api/v1/staff/" + staffId + "/deactivate", null, s.owner(), stepUp(s.owner(), PW)).status());
        assertEquals(401, post("/api/v1/sync/push", "{\"ops\":[" + pending + "]}", staff).status());
        assertEquals(401, get("/api/v1/sync/pull?since=0", staff).status());
        assertEquals(0, count("supplier", "id", sup), "nothing from a deactivated account is applied");

        // Reactivated and signed in again, the phone sends the change it kept; it is applied once.
        assertEquals(200, post("/api/v1/staff/" + staffId + "/activate", null, s.owner(), stepUp(s.owner(), PW)).status());
        Resp back = login(u, STAFF_PW);
        assertEquals(200, back.status(), back.raw());
        Phone phone = new Phone(back.str("accessToken"));
        assertEquals("APPLIED", status(phone.push(pending), 0));
        assertEquals("APPLIED", status(phone.push(pending), 0));
        assertEquals(1, count("supplier", "id", sup));
    }

    @Test
    void refusedChangesAreReportedNotDropped() {
        Shop s = shop();
        String sup = newId();
        Resp r = new Phone(s.viewer()).push(op(newId(), "SUPPLIER", sup, null, "supplier", supplierJson("No rights")));
        assertEquals("REJECTED", status(r, 0));
        assertEquals("PERMISSION_DENIED", text(result(r, 0).get("code")));
        assertEquals(0, count("supplier", "id", sup));
    }
}
