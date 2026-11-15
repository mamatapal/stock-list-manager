package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Batch tracking, expiry filters, links back to the purchase, permissions and isolation. */
class BatchExpiryIT extends PurchaseTestSupport {
    private static final String TODAY = "2026-11-14";

    private record Setup(Shop shop, String supplier, String purchaseId, List<String> lines, List<String> receipts) {}

    /**
     * One purchase, seven lines received with these expiry dates (relative to 2026-11-14):
     * -1 day, today, +5, +20, +60, +120, none.
     */
    private Setup setup() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Expiry Traders");
        String[] expiries = {"2026-11-13", "2026-11-14", "2026-11-19", "2026-12-04", "2027-01-13", "2027-03-14", null};
        List<String> lines = new ArrayList<>(), receipts = new ArrayList<>(), pl = new ArrayList<>(), rc = new ArrayList<>();
        for (int i = 0; i < expiries.length; i++) {
            String item = item(s.owner(), "Med " + i, PCS, null, null);
            String l = newId(), r = newId();
            lines.add(l);
            receipts.add(r);
            pl.add(pline(l, item, "10", PCS, "PER_UNIT", "5", null));
            rc.add(receipt(r, l, "10", "BATCH-" + i, expiries[i]));
        }
        Resp p = post("/api/v1/purchases", purchaseJson(sup, "EXP-1", "2026-11-01", true,
                delivery(newId(), null, "2026-11-02", rc.toArray(String[]::new)), pl.toArray(String[]::new)), s.owner());
        assertEquals(201, p.status(), p.raw());
        return new Setup(s, sup, p.str("id"), lines, receipts);
    }

    private List<String> batchNos(String token, String query) {
        Resp r = get("/api/v1/batches?" + query, token);
        assertEquals(200, r.status(), r.raw());
        List<String> out = new ArrayList<>();
        for (JsonNode b : r.body()) out.add(text(b.get("batchNo")));
        return out;
    }

    @Test
    void expiryFilters() {
        Setup x = setup();
        String t = x.shop().staff();
        assertEquals(List.of("BATCH-0"), batchNos(t, "expiry=EXPIRED&today=" + TODAY));
        assertEquals(List.of("BATCH-1", "BATCH-2"), batchNos(t, "expiry=WITHIN_7&today=" + TODAY), "today counts, soonest first");
        assertEquals(List.of("BATCH-1", "BATCH-2", "BATCH-3"), batchNos(t, "expiry=WITHIN_30&today=" + TODAY));
        assertEquals(List.of("BATCH-1", "BATCH-2", "BATCH-3", "BATCH-4"), batchNos(t, "expiry=WITHIN_90&today=" + TODAY));
        assertEquals(List.of("BATCH-0", "BATCH-1", "BATCH-2", "BATCH-3", "BATCH-4", "BATCH-5"),
                batchNos(t, "expiry=ALL&today=" + TODAY), "every batch with an expiry date");
        assertEquals(7, batchNos(t, "today=" + TODAY).size(), "batch tracking also lists batches without expiry");
        assertEquals(400, get("/api/v1/batches?expiry=SOON", t).status());

        JsonNode b = get("/api/v1/batches?expiry=EXPIRED&today=" + TODAY, t).body().get(0);
        assertEquals("-1", text(b.get("daysToExpiry")));
        assertEquals("Expired 1 day ago", text(b.get("expiryLabel")));
    }

    @Test
    void eachBatchLeadsBackToItsPurchase() {
        Setup x = setup();
        JsonNode b = get("/api/v1/batches?q=BATCH-3&today=" + TODAY, x.shop().owner()).body().get(0);
        assertEquals(x.purchaseId(), text(b.get("purchaseId")));
        assertEquals(x.lines().get(3), text(b.get("purchaseLineId")));
        assertEquals(x.receipts().get(3), text(b.get("id")), "the batch id is the receipt it came from");
        assertEquals("EXP-1", text(b.get("invoiceNo")));
        assertEquals("Expiry Traders", text(b.get("supplierName")));
        assertEquals("Med 3", text(b.get("itemName")));
        assertEquals("10", text(b.get("quantity")));

        Resp purchase = get("/api/v1/purchases/" + text(b.get("purchaseId")), x.shop().owner());
        assertEquals(200, purchase.status());
        String deliveryId = text(b.get("deliveryId"));
        assertEquals(deliveryId, text(purchase.body().get("deliveries").get(0).get("id")));
        assertEquals(200, get("/api/v1/batches/" + text(b.get("id")), x.shop().owner()).status());

        // Renaming the item later does not change the batch (it shows what was bought).
        String itemId = text(b.get("itemId"));
        Resp item = get("/api/v1/items/" + itemId, x.shop().owner());
        putIfMatch("/api/v1/items/" + itemId, itemJson("Renamed medicine", PCS, null, null, null), x.shop().owner(), version(item));
        assertEquals("Med 3", text(get("/api/v1/batches/" + text(b.get("id")), x.shop().owner()).body().get("itemName")));
    }

    @Test
    void correctedToZeroAndCancelledStockLeaveTheExpiryLists() {
        Setup x = setup();
        String owner = x.shop().owner();
        post("/api/v1/purchases/" + x.purchaseId() + "/deliveries", delivery(newId(), null, "2026-11-03",
                correction(newId(), x.lines().get(0), "-10", x.receipts().get(0), "Returned to supplier")), owner);
        assertTrue(batchNos(owner, "expiry=EXPIRED&today=" + TODAY).isEmpty());
        JsonNode zero = get("/api/v1/batches?q=BATCH-0&today=" + TODAY, owner).body().get(0);
        assertEquals("0", text(zero.get("quantity")), "still visible in batch tracking");
        assertEquals("10", text(zero.get("receivedQuantity")), "the original receipt is kept");

        Resp p = get("/api/v1/purchases/" + x.purchaseId(), owner);
        callWithHeader("POST", "/api/v1/purchases/" + x.purchaseId() + "/cancel", "{\"reason\":\"Wrong bill\"}", owner,
                "If-Match", Long.toString(version(p)));
        assertTrue(batchNos(owner, "expiry=ALL&today=" + TODAY).isEmpty());
        assertTrue(batchNos(owner, "today=" + TODAY).isEmpty());
        assertEquals(7, batchNos(owner, "includeCancelled=true&today=" + TODAY).size());
    }

    @Test
    void filtersBySearchItemSupplierAndReceivedDate() {
        Setup x = setup();
        String owner = x.shop().owner();
        assertEquals(List.of("BATCH-5"), batchNos(owner, "q=med%205&today=" + TODAY));
        assertEquals(7, batchNos(owner, "q=exp-1&today=" + TODAY).size(), "invoice number");
        assertEquals(7, batchNos(owner, "supplierId=" + x.supplier()).size());
        String other = supplier(owner, "Nobody");
        assertTrue(batchNos(owner, "supplierId=" + other).isEmpty());
        assertEquals(7, batchNos(owner, "receivedFrom=2026-11-02&receivedTo=2026-11-02").size());
        assertTrue(batchNos(owner, "receivedFrom=2026-11-03").isEmpty());
    }

    @Test
    void alertCountsUseTodayInTheBusinessTimeZone() {
        Shop s = shop();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        String sup = supplier(s.owner(), "Alert");
        String a = item(s.owner(), "Old milk", PCS, null, null), b = item(s.owner(), "New milk", PCS, null, null);
        String la = newId(), lb = newId();
        post("/api/v1/purchases", purchaseJson(sup, "AL", today.minusDays(10).toString(), true,
                delivery(newId(), null, today.minusDays(10).toString(), receipt(newId(), la, "1", null, today.minusDays(1).toString()),
                        receipt(newId(), lb, "1", null, today.plusDays(3).toString())),
                pline(la, a, "1", PCS, "PER_UNIT", "1", null), pline(lb, b, "1", PCS, "PER_UNIT", "1", null)), s.owner());
        Resp alert = get("/api/v1/batches/alert", s.staff());
        assertEquals(200, alert.status(), alert.raw());
        assertEquals("1", alert.str("expired"));
        assertEquals("1", alert.str("within7"));
        assertEquals(today.toString(), alert.str("today"));
    }

    @Test
    void permissionsAndIsolation() {
        Setup x = setup();
        assertEquals(403, get("/api/v1/batches", x.shop().viewer()).status());
        String u = unique("nx");
        Resp c = createStaff(x.shop().owner(), stepUp(x.shop().owner(), PW), u, "[\"PURCHASE_RECORD\"]");
        String noExpiry = staffSession(u, c.str("temporaryPassword")).str("accessToken");
        assertEquals(403, get("/api/v1/batches?expiry=ALL", noExpiry).status());
        assertEquals(0, get("/api/v1/sync/pull?since=0", noExpiry).body().get("batches").size());
        assertEquals(7, get("/api/v1/sync/pull?since=0", x.shop().staff()).body().get("batches").size());

        Shop other = shop();
        assertTrue(batchNos(other.owner(), "").isEmpty());
        String batchId = x.receipts().get(2);
        assertEquals(404, get("/api/v1/batches/" + batchId, other.owner()).status());
    }
}
