package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Supplier-wise and item-wise history, invoice and date filters, who did what, permissions and isolation. */
class HistoryIT extends PurchaseTestSupport {
    private static final String BOTTLE = "00000000-0000-0000-0000-00000000000c";

    private String userId(String token) {
        return get("/api/v1/auth/me", token).str("userId");
    }

    private Resp buy(String token, String supplier, String invoice, String date, boolean complete, String... lines) {
        Resp r = post("/api/v1/purchases", purchaseJson(supplier, invoice, date, complete, null, lines), token);
        assertEquals(201, r.status(), r.raw());
        return r;
    }

    private static List<String> invoices(JsonNode purchases) {
        List<String> out = new ArrayList<>();
        for (JsonNode p : purchases) out.add(text(p.get("invoiceNo")));
        return out;
    }

    @Test
    void supplierWiseHistory() {
        Shop s = shop();
        String a = supplier(s.owner(), "Agarwal"), b = supplier(s.owner(), "Bansal");
        String rice = item(s.owner(), "Rice", KG, null, null);
        buy(s.owner(), a, "A-1", "2026-10-01", true, pline(newId(), rice, "10", KG, "PER_UNIT", "50", null));
        buy(s.owner(), a, "A-2", "2026-11-01", true, pline(newId(), rice, "5", KG, "TOTAL", "300", null));
        buy(s.owner(), a, "A-draft", "2026-11-02", false, pline(newId(), rice, "1", KG, "PER_UNIT", "1", null));
        Resp cancelled = buy(s.owner(), a, "A-x", "2026-11-03", true, pline(newId(), rice, "100", KG, "PER_UNIT", "1", null));
        callWithHeader("POST", "/api/v1/purchases/" + cancelled.str("id") + "/cancel", "{\"reason\":\"Duplicate\"}",
                s.owner(), "If-Match", Long.toString(version(cancelled)));
        buy(s.owner(), b, "B-1", "2026-11-01", true, pline(newId(), rice, "1", KG, "PER_UNIT", "60", null));

        Resp h = get("/api/v1/purchases/by-supplier/" + a, s.staff());
        assertEquals(200, h.status(), h.raw());
        assertEquals("Agarwal", h.str("supplierName"));
        assertEquals("2", h.str("purchaseCount"), "drafts and cancelled purchases are not counted");
        assertEquals("800.00", h.str("totalCost"));
        assertEquals("2026-10-01", h.str("firstPurchaseDate"));
        assertEquals("2026-11-01", h.str("lastPurchaseDate"));
        assertEquals(List.of("A-x", "A-2", "A-1"), invoices(h.body().get("purchases")), "newest first; cancelled shown, draft not");
        assertEquals("CANCELLED", text(h.body().get("purchases").get(0).get("status")));

        Resp oct = get("/api/v1/purchases/by-supplier/" + a + "?from=2026-10-01&to=2026-10-31", s.staff());
        assertEquals(List.of("A-1"), invoices(oct.body().get("purchases")));
        assertEquals("500.00", oct.str("totalCost"));
    }

    @Test
    void itemWiseHistoryKeepsHistoricalQuantitiesAndPrices() {
        Shop s = shop();
        String a = supplier(s.owner(), "Chawla"), b = supplier(s.owner(), "Dutta");
        String oil = item(s.owner(), "Mustard Oil", LITRE, "100", "120");
        String l1 = newId();
        Resp first = buy(s.owner(), a, "C-1", "2026-09-01", true, pline(l1, oil, "10", LITRE, "PER_UNIT", "110", "130"));
        buy(s.owner(), b, "D-1", "2026-10-01", true, pline(newId(), oil, "20", LITRE, "TOTAL", "2500", "140"));
        buy(s.owner(), a, "C-2", "2026-11-01", true, pline(newId(), oil, "5", LITRE, "PER_UNIT", "118.50", null));
        buy(s.owner(), a, "C-draft", "2026-11-05", false, pline(newId(), oil, "1", LITRE, "PER_UNIT", "1", null));

        // Later the catalogue item is renamed and repriced: history keeps what was billed.
        Resp item = get("/api/v1/items/" + oil, s.owner());
        putIfMatch("/api/v1/items/" + oil, itemJson("Kachi Ghani Mustard Oil", LITRE, "200", "220", null), s.owner(), version(item));

        Resp h = get("/api/v1/purchases/by-item/" + oil, s.staff());
        assertEquals(200, h.status(), h.raw());
        JsonNode rows = h.body().get("rows");
        assertEquals(3, rows.size(), "drafts are not history");
        assertEquals(List.of("C-2", "D-1", "C-1"), invoices(rows), "newest first");
        JsonNode oldest = rows.get(2);
        assertEquals("Mustard Oil", text(oldest.get("itemName")));
        assertEquals("10", text(oldest.get("quantity")));
        assertEquals("110", text(oldest.get("purchasePrice")));
        assertEquals("130", text(oldest.get("sellingPrice")));
        assertEquals("1100.00", text(oldest.get("totalCost")));
        assertEquals("Chawla", text(oldest.get("supplierName")));
        assertEquals(first.str("id"), text(oldest.get("purchaseId")), "each row opens its purchase");
        assertEquals(l1, text(oldest.get("purchaseLineId")));
        JsonNode total = rows.get(1);
        assertEquals("2500", text(total.get("purchasePrice")));
        assertEquals("125", text(total.get("purchaseUnitPrice")));
        assertEquals("2500.00", text(total.get("totalCost")), "a total amount is kept as entered");

        assertEquals("3", h.str("purchaseCount"));
        assertEquals("35", h.str("totalQuantity"));
        assertEquals("4192.50", h.str("totalCost"));
        assertEquals("110", h.str("lowestUnitPrice"));
        assertEquals("125", h.str("highestUnitPrice"));

        Resp range = get("/api/v1/purchases/by-item/" + oil + "?from=2026-10-01&to=2026-10-31", s.staff());
        assertEquals(List.of("D-1"), invoices(range.body().get("rows")));

        // A purchase in another unit: listed, but quantities are not added across units.
        buy(s.owner(), a, "C-3", "2026-11-10", true, pline(newId(), oil, "2", BOTTLE, "PER_UNIT", "250", null));
        assertNull(get("/api/v1/purchases/by-item/" + oil, s.staff()).str("totalQuantity"));
    }

    @Test
    void invoiceSearchAndDateRange() {
        Shop s = shop();
        String a = supplier(s.owner(), "Elango");
        String it = item(s.owner(), "Tea", PCS, null, null);
        buy(s.owner(), a, "GST/2026/0042", "2026-11-01", true, pline(newId(), it, "1", PCS, "PER_UNIT", "1", null));
        buy(s.owner(), a, "GST/2026/0043", "2026-11-05", true, pline(newId(), it, "1", PCS, "PER_UNIT", "1", null));
        buy(s.owner(), a, "RCPT-9", "2026-11-09", true, pline(newId(), it, "1", PCS, "PER_UNIT", "1", null));
        assertEquals(List.of("GST/2026/0042"), invoices(get("/api/v1/purchases?q=0042", s.staff()).body()));
        assertEquals(List.of("GST/2026/0043", "GST/2026/0042"), invoices(get("/api/v1/purchases?q=gst/2026", s.staff()).body()));
        assertEquals(List.of("GST/2026/0043"), invoices(get("/api/v1/purchases?from=2026-11-02&to=2026-11-08", s.staff()).body()));
        assertEquals(List.of("RCPT-9", "GST/2026/0043"), invoices(get("/api/v1/purchases?from=2026-11-05", s.staff()).body()));
        assertTrue(invoices(get("/api/v1/purchases?q=0042&from=2026-11-02", s.staff()).body()).isEmpty());
    }

    @Test
    void recordsShowWhoCreatedAndChangedThem() {
        Shop s = shop();
        String ownerId = userId(s.owner()), staffId = userId(s.staff());
        String sup = supplier(s.staff(), "Farooq");
        String it = item(s.owner(), "Ghee", KG, null, null);
        String line = newId();
        Resp p = buy(s.staff(), sup, "F-1", "2026-11-01", true, pline(line, it, "2", KG, "PER_UNIT", "500", null));
        assertEquals(staffId, p.str("createdBy"));
        assertEquals(staffId, p.str("completedBy"));
        Resp edited = putIfMatch("/api/v1/purchases/" + p.str("id"), purchaseJson(sup, "F-1", "2026-11-01", true, null,
                pline(line, it, "2", KG, "PER_UNIT", "480", null)), s.owner(), version(p));
        assertEquals(staffId, edited.str("createdBy"));
        assertEquals(ownerId, edited.str("updatedBy"));
        assertEquals(ownerId, text(get("/api/v1/purchases/" + p.str("id") + "/revisions", s.owner()).body().get(0).get("changedBy")));
        Resp d = post("/api/v1/purchases/" + p.str("id") + "/deliveries", delivery(newId(), null, "2026-11-02",
                receipt(newId(), line, "2", null, null)), s.staff());
        assertEquals(staffId, text(d.body().get("deliveries").get(0).get("receivedBy")));
        assertEquals(staffId, get("/api/v1/suppliers/" + sup, s.owner()).str("createdBy"));
        assertEquals(staffId, text(get("/api/v1/batches", s.owner()).body().get(0).get("receivedBy")));

        // Names for those ids, only for this business.
        Resp people = get("/api/v1/people", s.staff());
        assertEquals(200, people.status());
        assertEquals(3, people.body().size(), "owner, staff and viewer");
        boolean ownerListed = false;
        for (JsonNode n : people.body()) {
            if (ownerId.equals(text(n.get("id")))) { ownerListed = true; assertEquals("OWNER", text(n.get("role"))); }
            assertNull(n.get("username"), "no usernames or contact details");
            assertNull(n.get("mobile"));
        }
        assertTrue(ownerListed);
        assertEquals(3, get("/api/v1/sync/pull?since=0", s.staff()).body().get("people").size());
        assertEquals(3, get("/api/v1/people", shop().owner()).body().size(), "another business sees only its own people");
    }

    @Test
    void permissionsAndIsolation() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Gill");
        String it = item(s.owner(), "Salt", PCS, null, null);
        buy(s.owner(), sup, "G-1", "2026-11-01", true, pline(newId(), it, "1", PCS, "PER_UNIT", "1", null));
        assertEquals(403, get("/api/v1/purchases/by-supplier/" + sup, s.viewer()).status());
        assertEquals(403, get("/api/v1/purchases/by-item/" + it, s.viewer()).status());
        Shop other = shop();
        assertEquals(404, get("/api/v1/purchases/by-supplier/" + sup, other.owner()).status());
        assertEquals(404, get("/api/v1/purchases/by-item/" + it, other.owner()).status());
        assertEquals(0, get("/api/v1/purchases?q=G-1", other.owner()).body().size());
    }
}
