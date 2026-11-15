package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Purchases and deliveries through phone sync: duplicates, offline order, profit hiding, other phones. */
class PurchaseSyncIT extends PurchaseTestSupport {

    private Resp push(String token, String... ops) {
        return post("/api/v1/sync/push", "{\"ops\":[" + String.join(",", ops) + "]}", token);
    }

    private static String purchaseOp(String opId, String id, Long base, String purchase) {
        return """
                {"opId":"%s","entityType":"PURCHASE","entityId":"%s","action":"UPSERT","baseVersion":%s,"purchase":%s}
                """.formatted(opId, id, base, purchase);
    }

    private static String deliveryOp(String opId, String deliveryId, String delivery) {
        return """
                {"opId":"%s","entityType":"DELIVERY","entityId":"%s","action":"UPSERT","baseVersion":null,"delivery":%s}
                """.formatted(opId, deliveryId, delivery);
    }

    private static String status(Resp r, int i) {
        return text(r.body().get("results").get(i).get("status"));
    }

    @Test
    void samePurchasePushedThreeTimesIsStoredOnce() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Retry");
        String it = item(s.owner(), "Retry item", PCS, null, null);
        String op = newId(), id = newId(), line = newId(), dId = newId();
        String body = purchaseJson(sup, "R-1", "2026-11-10", true,
                delivery(dId, null, "2026-11-10", receipt(newId(), line, "4", null, null)),
                pline(line, it, "10", PCS, "PER_UNIT", "5", "6"));
        for (int i = 0; i < 3; i++) {
            Resp r = push(s.staff(), purchaseOp(op, id, null, body));
            assertEquals("APPLIED", status(r, 0), r.raw());
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from purchase where id = uuid_to_bin(?)", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from purchase_delivery where purchase_id = uuid_to_bin(?)",
                Integer.class, id));
    }

    @Test
    void deliverySentTwiceUnderDifferentOperationsIsRecordedOnce() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Twice");
        String it = item(s.owner(), "Twice item", PCS, null, null);
        String id = newId(), line = newId();
        push(s.owner(), purchaseOp(newId(), id, null, purchaseJson(sup, "T", "2026-11-10", true, null,
                pline(line, it, "10", PCS, "PER_UNIT", "5", null))));
        String dId = newId();
        String d = delivery(dId, id, "2026-11-11", receipt(newId(), line, "6", null, null));
        Resp a = push(s.staff(), deliveryOp(newId(), dId, d));
        Resp b = push(s.staff(), deliveryOp(newId(), dId, d));
        assertEquals("APPLIED", status(a, 0), a.raw());
        assertEquals("APPLIED", status(b, 0), b.raw());
        JsonNode p = b.body().get("results").get(0).get("purchase");
        assertEquals("PARTIAL", text(p.get("status")));
        assertEquals("6", text(p.get("lines").get(0).get("receivedQuantity")));
    }

    @Test
    void anOfflinePhoneSendsTheNewItemThePurchaseAndTheDeliveryInOrder() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Offline");
        String newItem = newId(), id = newId(), line = newId(), dId = newId();
        String itemOp = """
                {"opId":"%s","entityType":"ITEM","entityId":"%s","action":"UPSERT","baseVersion":null,
                 "item":{"name":"Brand new item","defaultUnitId":"%s"}}""".formatted(newId(), newItem, PCS);
        Resp r = push(s.owner(), itemOp,
                purchaseOp(newId(), id, null, purchaseJson(sup, "OFF-1", "2026-11-10", true, null,
                        pline(line, newItem, "12", PCS, "PER_UNIT", "8", "10"))),
                deliveryOp(newId(), dId, delivery(dId, id, "2026-11-10", receipt(newId(), line, "12", null, null))));
        assertEquals("APPLIED", status(r, 0), r.raw());
        assertEquals("APPLIED", status(r, 1), r.raw());
        assertEquals("APPLIED", status(r, 2), r.raw());
        assertEquals("RECEIVED", text(r.body().get("results").get(2).get("purchase").get("status")));
    }

    @Test
    void otherPhonesReceiveIt_withoutProfitWhenNotAllowed() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Pull");
        String it = item(s.owner(), "Pull item", PCS, null, null);
        long staffCursor = Long.parseLong(get("/api/v1/sync/pull?since=0", s.staff()).str("nextSince"));
        String id = newId();
        push(s.owner(), purchaseOp(newId(), id, null, purchaseJson(sup, "P-1", "2026-11-10", true, null,
                pline(newId(), it, "10", PCS, "PER_UNIT", "5", "8"))));

        Resp staffPull = get("/api/v1/sync/pull?since=" + staffCursor, s.staff());
        JsonNode p = staffPull.body().get("purchases").get(0);
        assertEquals(id, text(p.get("id")));
        assertEquals("50.00", text(p.get("totalCost")));
        assertNull(text(p.get("totalExpectedProfit")), "profit never reaches a phone without permission");
        assertNull(text(p.get("lines").get(0).get("expectedProfit")));

        Resp ownerPull = get("/api/v1/sync/pull?since=0", s.owner());
        boolean found = false;
        for (JsonNode n : ownerPull.body().get("purchases")) {
            if (id.equals(text(n.get("id")))) {
                assertEquals("30.00", text(n.get("totalExpectedProfit")));
                found = true;
            }
        }
        assertTrue(found);
        assertEquals(0, get("/api/v1/sync/pull?since=0", s.viewer()).body().get("purchases").size(),
                "no purchase permission, no purchases");
    }

    @Test
    void cancelThroughSyncNeedsAReason() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Cancel");
        String it = item(s.owner(), "Cancel item", PCS, null, null);
        String id = newId();
        Resp created = push(s.owner(), purchaseOp(newId(), id, null, purchaseJson(sup, "C", "2026-11-10", true, null,
                pline(newId(), it, "1", PCS, "PER_UNIT", "5", null))));
        long v = Long.parseLong(text(created.body().get("results").get(0).get("version")));
        String noReason = """
                {"opId":"%s","entityType":"PURCHASE","entityId":"%s","action":"DELETE","baseVersion":%d}
                """.formatted(newId(), id, v);
        assertEquals("REJECTED", status(push(s.owner(), noReason), 0));
        String withReason = """
                {"opId":"%s","entityType":"PURCHASE","entityId":"%s","action":"DELETE","baseVersion":%d,"reason":"Duplicate bill"}
                """.formatted(newId(), id, v);
        Resp done = push(s.owner(), withReason);
        assertEquals("APPLIED", status(done, 0), done.raw());
        assertEquals("CANCELLED", text(done.body().get("results").get(0).get("purchase").get("status")));
    }
}
