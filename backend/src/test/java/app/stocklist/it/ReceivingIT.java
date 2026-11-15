package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Partial deliveries against purchases and out-of-stock lists, corrections, duplicates and edits. */
class ReceivingIT extends PurchaseTestSupport {

    private record Setup(Shop shop, String supplier, String item, String listId, String listLine, String purchaseId,
                         String purchaseLine, long purchaseVersion) {}

    /** List of 100 PCS, and a completed purchase of 100 PCS linked to it, nothing received yet. */
    private Setup setup() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Kumar");
        String soap = item(s.owner(), "Soap", PCS, null, null);
        String listLine = newId();
        Resp list = post("/api/v1/stock-lists", listJson("Soap order", "SAVED", sup,
                "{\"id\":\"" + listLine + "\",\"itemId\":\"" + soap + "\",\"itemName\":\"Soap\",\"quantity\":\"100\","
                        + "\"unitId\":\"" + PCS + "\",\"priceBasis\":\"PER_UNIT\",\"enteredPrice\":\"20\"}"), s.owner());
        assertEquals(201, list.status(), list.raw());
        String pl = newId();
        Resp p = post("/api/v1/purchases", purchaseJson(sup, "K-1", "2026-11-10", list.str("id"), true, null,
                pline(pl, soap, "100", PCS, "PER_UNIT", "20", "25", listLine)), s.owner());
        assertEquals(201, p.status(), p.raw());
        return new Setup(s, sup, soap, list.str("id"), listLine, p.str("id"), pl, version(p));
    }

    private JsonNode listLine(Setup x) {
        Resp list = get("/api/v1/stock-lists/" + x.listId() + "/receiving", x.shop().owner());
        assertEquals(200, list.status(), list.raw());
        return list.body().get("lines").get(0);
    }

    private Resp receive(Setup x, String token, String deliveryId, String date, String... lines) {
        return post("/api/v1/purchases/" + x.purchaseId() + "/deliveries", delivery(deliveryId, null, date, lines),
                token);
    }

    @Test
    void workedExampleFromTheRequirements() {
        Setup x = setup();
        JsonNode l = listLine(x);
        assertEquals("100", field(l, "quantity"));
        assertEquals("0", field(l, "receivedQuantity"));
        assertEquals("PENDING", field(l, "receivingStatus"));
        assertEquals("PENDING", get("/api/v1/purchases/" + x.purchaseId(), x.shop().owner()).str("status"));

        Resp d1 = receive(x, x.shop().staff(), newId(), "2026-11-11", receipt(newId(), x.purchaseLine(), "60", "B-1",
                "2027-05-31"));
        assertEquals(201, d1.status(), d1.raw());
        assertEquals("PARTIAL", d1.str("status"));
        assertEquals("2026-11-11", d1.str("receivedDate"));
        assertEquals("40", field(lineOf(d1, x.purchaseLine()), "remainingQuantity"));
        l = listLine(x);
        assertEquals("100", field(l, "quantity"), "the requested quantity never changes");
        assertEquals("60", field(l, "receivedQuantity"));
        assertEquals("40", field(l, "remainingQuantity"));
        assertEquals("PARTIAL", field(l, "receivingStatus"));
        assertEquals("PARTIAL", get("/api/v1/stock-lists/" + x.listId(), x.shop().owner()).str("receivingStatus"));

        Resp d2 = receive(x, x.shop().owner(), newId(), "2026-11-13", receipt(newId(), x.purchaseLine(), "40", null,
                null));
        assertEquals("RECEIVED", d2.str("status"));
        assertEquals("2026-11-13", d2.str("receivedDate"));
        assertEquals(2, d2.body().get("deliveries").size(), "each delivery is kept separately");
        l = listLine(x);
        assertEquals("100", field(l, "receivedQuantity"));
        assertEquals("0", field(l, "remainingQuantity"));
        assertEquals("FULL", field(l, "receivingStatus"));
        assertEquals("FULL", get("/api/v1/stock-lists/" + x.listId(), x.shop().owner()).str("receivingStatus"));
        Integer batches = jdbc.queryForObject("select count(*) from batch where purchase_id = uuid_to_bin(?)",
                Integer.class, x.purchaseId());
        assertEquals(2, batches);
        Integer requested = jdbc.queryForObject("select count(*) from stock_list_item where id = uuid_to_bin(?) "
                + "and quantity = 100", Integer.class, x.listLine());
        assertEquals(1, requested);
    }

    @Test
    void receivingDoesNotMakeAListEditLookLikeAConflict() {
        Setup x = setup();
        Resp list = get("/api/v1/stock-lists/" + x.listId(), x.shop().owner());
        long before = version(list);
        receive(x, x.shop().owner(), newId(), "2026-11-11", receipt(newId(), x.purchaseLine(), "10", null, null));
        assertEquals(before, version(get("/api/v1/stock-lists/" + x.listId(), x.shop().owner())));
        assertEquals(x.purchaseVersion(), version(get("/api/v1/purchases/" + x.purchaseId(), x.shop().owner())));
    }

    @Test
    void sameDeliverySentTwiceIsRecordedOnce() {
        Setup x = setup();
        String id = newId();
        String lineId = newId();
        Resp a = receive(x, x.shop().staff(), id, "2026-11-11", receipt(lineId, x.purchaseLine(), "30", null, null));
        Resp b = receive(x, x.shop().staff(), id, "2026-11-11", receipt(lineId, x.purchaseLine(), "30", null, null));
        assertEquals(201, a.status(), a.raw());
        assertEquals(201, b.status(), b.raw());
        assertEquals(1, b.body().get("deliveries").size());
        assertEquals("30", field(listLine(x), "receivedQuantity"));
    }

    @Test
    void overReceiptAndCorrections() {
        Setup x = setup();
        String receiptId = newId();
        Resp d = receive(x, x.shop().owner(), newId(), "2026-11-11", receipt(receiptId, x.purchaseLine(), "110", "B9",
                null));
        assertEquals("RECEIVED", d.str("status"));
        assertTrue(d.body().get("overReceived").asBoolean());
        JsonNode l = listLine(x);
        assertEquals("0", field(l, "remainingQuantity"));
        assertEquals("10", field(l, "overBy"));

        Resp noReason = receive(x, x.shop().owner(), newId(), "2026-11-12", correction(newId(), x.purchaseLine(), "-10",
                receiptId, null));
        assertEquals(400, noReason.status(), "a correction needs a reason");
        Resp fix = receive(x, x.shop().owner(), newId(), "2026-11-12", correction(newId(), x.purchaseLine(), "-10",
                receiptId, "Counted twice"));
        assertEquals(201, fix.status(), fix.raw());
        assertFalse(fix.body().get("overReceived").asBoolean());
        assertEquals("100", field(listLine(x), "receivedQuantity"));
        Integer batch = jdbc.queryForObject("select count(*) from batch where purchase_id = uuid_to_bin(?) and "
                + "quantity = 100", Integer.class, x.purchaseId());
        assertEquals(1, batch, "batch quantity follows the correction");

        Resp tooMuch = receive(x, x.shop().owner(), newId(), "2026-11-12", correction(newId(), x.purchaseLine(),
                "-101", receiptId, "Oops"));
        assertEquals(400, tooMuch.status(), "received quantity cannot go negative");
        Integer original = jdbc.queryForObject("select count(*) from purchase_delivery_item where id = uuid_to_bin(?) "
                + "and quantity_received = 110", Integer.class, receiptId);
        assertEquals(1, original, "the original receipt is never changed");
    }

    @Test
    void severalPurchasesAndUnitsAgainstOneListLine() {
        Setup x = setup();
        receive(x, x.shop().owner(), newId(), "2026-11-11", receipt(newId(), x.purchaseLine(), "50", null, null));
        String other = supplier(x.shop().owner(), "Second supplier");
        String pl2 = newId();
        Resp p2 = post("/api/v1/purchases", purchaseJson(other, "S-1", "2026-11-12", true,
                delivery(newId(), null, "2026-11-12", receipt(newId(), pl2, "30", null, null)),
                pline(pl2, x.item(), "30", PCS, "PER_UNIT", "21", null, x.listLine())), x.shop().owner());
        assertEquals(201, p2.status(), p2.raw());
        assertEquals("RECEIVED", p2.str("status"), "goods that came with the bill");
        assertEquals("80", field(listLine(x), "receivedQuantity"));

        String pl3 = newId();
        Resp boxes = post("/api/v1/purchases", purchaseJson(other, "S-2", "2026-11-12", true,
                delivery(newId(), null, "2026-11-12", receipt(newId(), pl3, "2", null, null)),
                pline(pl3, x.item(), "2", "00000000-0000-0000-0000-000000000007", "PER_UNIT", "400", null,
                        x.listLine())), x.shop().owner());
        assertEquals(201, boxes.status(), boxes.raw());
        JsonNode l = listLine(x);
        assertEquals("80", field(l, "receivedQuantity"), "boxes are not counted as pieces");
        assertTrue(l.get("unitMismatch").asBoolean());

        // A cancelled purchase stops counting.
        Resp cancel = callWithHeader("POST", "/api/v1/purchases/" + p2.str("id") + "/cancel", "{\"reason\":\"Returned\"}",
                x.shop().owner(), "If-Match", Long.toString(version(p2)));
        assertEquals(200, cancel.status(), cancel.raw());
        assertEquals("50", field(listLine(x), "receivedQuantity"));
    }

    @Test
    void editsCannotUndoWhatWasReceived() {
        Setup x = setup();
        receive(x, x.shop().owner(), newId(), "2026-11-11", receipt(newId(), x.purchaseLine(), "60", null, null));
        long v = version(get("/api/v1/purchases/" + x.purchaseId(), x.shop().owner()));
        Resp below = putIfMatch("/api/v1/purchases/" + x.purchaseId(), purchaseJson(x.supplier(), "K-1", "2026-11-10",
                x.listId(), true, null, pline(x.purchaseLine(), x.item(), "50", PCS, "PER_UNIT", "20", "25",
                        x.listLine())), x.shop().owner(), v);
        assertEquals(400, below.status());
        assertEquals("BELOW_RECEIVED", below.str("code"));
        Resp removed = putIfMatch("/api/v1/purchases/" + x.purchaseId(), purchaseJson(x.supplier(), "K-1",
                "2026-11-10", x.listId(), true, null, pline(newId(), x.item(), "5", PCS, "PER_UNIT", "20", null)),
                x.shop().owner(), v);
        assertEquals(400, removed.status());
        assertEquals("LINE_RECEIVED", removed.str("code"));
        Resp more = putIfMatch("/api/v1/purchases/" + x.purchaseId(), purchaseJson(x.supplier(), "K-1", "2026-11-10",
                x.listId(), true, null, pline(x.purchaseLine(), x.item(), "60", PCS, "PER_UNIT", "20", "25",
                        x.listLine())), x.shop().owner(), v);
        assertEquals(200, more.status(), more.raw());
        assertEquals("RECEIVED", more.str("status"), "billed 60, received 60");

        // The list line is linked to a purchase, so it cannot be dropped from the list.
        Resp list = get("/api/v1/stock-lists/" + x.listId(), x.shop().owner());
        Resp drop = putIfMatch("/api/v1/stock-lists/" + x.listId(), listJson("Soap order", "SAVED", x.supplier(),
                line(newId(), "Other", "1", PCS, "PER_UNIT", null)), x.shop().owner(), version(list));
        assertEquals(400, drop.status());
        assertEquals("LINE_PURCHASED", drop.str("code"));
    }

    @Test
    void draftsAndCancelledPurchasesCannotReceive() {
        Setup x = setup();
        String pl = newId();
        Resp draft = post("/api/v1/purchases", purchaseJson(x.supplier(), "D", "2026-11-10", false, null,
                pline(pl, x.item(), "5", PCS, "PER_UNIT", "20", null)), x.shop().owner());
        Resp r = post("/api/v1/purchases/" + draft.str("id") + "/deliveries", delivery(newId(), null, "2026-11-11",
                receipt(newId(), pl, "5", null, null)), x.shop().owner());
        assertEquals(400, r.status());
        assertEquals("PURCHASE_NOT_COMPLETED", r.str("code"));
        assertEquals(403, receive(x, x.shop().viewer(), newId(), "2026-11-11", receipt(newId(), x.purchaseLine(), "1",
                null, null)).status(), "needs PURCHASE_RECORD");
    }
}
