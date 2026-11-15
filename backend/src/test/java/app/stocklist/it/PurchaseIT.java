package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Purchase calculations, lifecycle, price snapshots, revisions, permissions, isolation and atomic saving. */
class PurchaseIT extends PurchaseTestSupport {

    @Test
    void calculationsFollowTheRules() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Gupta Oils");
        String oil = item(s.owner(), "Sunflower Oil", LITRE, "130", "150");
        String rice = item(s.owner(), "Basmati Rice", KG, null, null);
        String soap = item(s.owner(), "Soap", PCS, null, null);
        String l1 = newId(), l2 = newId(), l3 = newId();
        Resp p = post("/api/v1/purchases", purchaseJson(sup, "INV-1", "2026-11-10", true, null,
                pline(l1, oil, "20", LITRE, "PER_UNIT", "140", "165"),
                pline(l2, rice, "1.5", KG, "TOTAL", "270", null),
                pline(l3, soap, "10", PCS, "PER_UNIT", "5", "0")), s.owner());
        assertEquals(201, p.status(), p.raw());

        JsonNode a = lineOf(p, l1);
        assertEquals("2800.00", field(a, "totalCost"));
        assertEquals("3300.00", field(a, "expectedSales"));
        assertEquals("500.00", field(a, "expectedProfit"));
        assertEquals("15.15", field(a, "marginPercent"));
        assertEquals("17.86", field(a, "markupPercent"));

        JsonNode b = lineOf(p, l2);
        assertEquals("270.00", field(b, "totalCost"), "a total amount is not multiplied by 1.5");
        assertEquals("180", field(b, "purchaseUnitPrice"));
        assertNull(field(b, "expectedSales"));
        assertNull(field(b, "marginPercent"));

        JsonNode c = lineOf(p, l3);
        assertEquals("-50.00", field(c, "expectedProfit"));
        assertNull(field(c, "marginPercent"), "N/A: no sales value");
        assertEquals("-100.00", field(c, "markupPercent"));
        assertTrue(c.get("sellingBelowCost").asBoolean(), "warning, but saving is allowed");

        assertEquals("3120.00", p.str("totalCost"));
        assertEquals("3300.00", p.str("totalExpectedSales"));
        assertEquals("450.00", p.str("totalExpectedProfit"));
        // only lines with both prices: profit 450 / sales 3300, 450 / cost 2850
        assertEquals("13.64", p.str("marginPercent"));
        assertEquals("15.79", p.str("markupPercent"));
        assertEquals("PENDING", p.str("status"));
        assertEquals(3, Integer.parseInt(p.str("itemCount")));
    }

    @Test
    void draftThenCompleteUpdatesTheCatalogueOnlyWhenCompleted() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Sharma");
        String oil = item(s.owner(), "Oil", LITRE, "130", "150");
        String line = newId();
        Resp draft = post("/api/v1/purchases", purchaseJson(sup, "A1", "2026-11-10", false, null,
                pline(line, oil, "10", LITRE, "PER_UNIT", "140", "165")), s.owner());
        assertEquals(201, draft.status(), draft.raw());
        assertEquals("DRAFT", draft.str("status"));
        assertEquals("130", get("/api/v1/items/" + oil, s.owner()).str("latestPurchasePrice"), "drafts change nothing");

        Resp done = putIfMatch("/api/v1/purchases/" + draft.str("id"), purchaseJson(sup, "A1", "2026-11-10", true,
                null, pline(line, oil, "10", LITRE, "PER_UNIT", "140", "165")), s.owner(), version(draft));
        assertEquals(200, done.status(), done.raw());
        assertEquals("PENDING", done.str("status"));
        Resp item = get("/api/v1/items/" + oil, s.owner());
        assertEquals("140", item.str("latestPurchasePrice"));
        assertEquals("165", item.str("latestSellingPrice"));
        assertEquals("2026-11-10", item.str("lastPurchaseDate"));

        Resp back = putIfMatch("/api/v1/purchases/" + done.str("id"), purchaseJson(sup, "A1", "2026-11-10", false,
                null, pline(line, oil, "10", LITRE, "PER_UNIT", "140", "165")), s.owner(), version(done));
        assertEquals(400, back.status());
        assertEquals("ALREADY_COMPLETED", back.str("code"));

        // An older bill entered later does not overwrite newer catalogue prices.
        assertEquals(201, post("/api/v1/purchases", purchaseJson(sup, "OLD", "2026-10-01", true, null,
                pline(newId(), oil, "5", LITRE, "PER_UNIT", "100", "120")), s.owner()).status());
        assertEquals("140", get("/api/v1/items/" + oil, s.owner()).str("latestPurchasePrice"));
    }

    @Test
    void historicalPricesNeverChange() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Mehta");
        String oil = item(s.owner(), "Mustard Oil", LITRE, "120", "140");
        String line = newId();
        Resp first = post("/api/v1/purchases", purchaseJson(sup, "M-1", "2026-11-01", true, null,
                pline(line, oil, "10", LITRE, "PER_UNIT", "125", "145")), s.owner());
        assertEquals(201, first.status(), first.raw());

        // Catalogue rename and price change, then a newer purchase at different prices.
        Resp item = get("/api/v1/items/" + oil, s.owner());
        assertEquals(200, putIfMatch("/api/v1/items/" + oil, itemJson("Kachi Ghani Mustard Oil", LITRE, "160", "190",
                null), s.owner(), version(item)).status());
        assertEquals(201, post("/api/v1/purchases", purchaseJson(sup, "M-2", "2026-11-12", true, null,
                pline(newId(), oil, "10", LITRE, "PER_UNIT", "170", "200")), s.owner()).status());

        JsonNode old = lineOf(get("/api/v1/purchases/" + first.str("id"), s.owner()), line);
        assertEquals("Mustard Oil", field(old, "itemName"), "item name snapshot kept");
        assertEquals("LITRE", field(old, "unitCode"));
        assertEquals("125", field(old, "purchasePrice"));
        assertEquals("145", field(old, "sellingPrice"));
        assertEquals("1250.00", field(old, "totalCost"));
        assertEquals("1450.00", field(old, "expectedSales"));
        Integer rows = jdbc.queryForObject("select count(*) from purchase_item where item_name_snapshot = 'Mustard Oil'"
                + " and purchase_unit_price = 125 and purchase_id = uuid_to_bin(?)", Integer.class, first.str("id"));
        assertEquals(1, rows);
    }

    @Test
    void editingACompletedPurchaseKeepsTheEarlierVersion() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Rao");
        String sugar = item(s.owner(), "Sugar", KG, null, null);
        String line = newId();
        Resp p = post("/api/v1/purchases", purchaseJson(sup, "R-9", "2026-11-05", true, null,
                pline(line, sugar, "50", KG, "PER_UNIT", "42", "48")), s.owner());
        Resp edited = putIfMatch("/api/v1/purchases/" + p.str("id"), purchaseJson(sup, "R-9", "2026-11-05", true, null,
                pline(line, sugar, "50", KG, "PER_UNIT", "41.50", "48")), s.owner(), version(p));
        assertEquals(200, edited.status(), edited.raw());
        assertEquals("2075.00", edited.str("totalCost"));
        assertEquals(1, Integer.parseInt(edited.str("revisionNo")));

        Resp revs = get("/api/v1/purchases/" + p.str("id") + "/revisions", s.owner());
        assertEquals(200, revs.status(), revs.raw());
        assertEquals(1, revs.body().size());
        JsonNode before = revs.body().get(0).get("before");
        assertEquals("2100.00", text(before.get("totalCost")));
        assertEquals("42", text(before.get("lines").get(0).get("purchasePrice")));

        // A stale copy cannot overwrite the newer version.
        Resp stale = putIfMatch("/api/v1/purchases/" + p.str("id"), purchaseJson(sup, "R-9", "2026-11-05", true, null,
                pline(line, sugar, "50", KG, "PER_UNIT", "40", "48")), s.owner(), version(p));
        assertEquals(409, stale.status());
    }

    @Test
    void cancelInsteadOfDelete() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Iyer");
        String tea = item(s.owner(), "Tea", PCS, null, null);
        Resp p = post("/api/v1/purchases", purchaseJson(sup, "I-1", "2026-11-05", true, null,
                pline(newId(), tea, "4", PCS, "PER_UNIT", "250", "300")), s.owner());
        String id = p.str("id");
        Resp del = deleteIfMatch("/api/v1/purchases/" + id, s.owner(), version(p));
        assertEquals(400, del.status(), "a completed purchase needs a reason and is cancelled, not deleted");
        assertEquals("REASON_REQUIRED", del.str("code"));

        Resp cancel = callWithHeader("POST", "/api/v1/purchases/" + id + "/cancel", "{\"reason\":\"Wrong supplier\"}",
                s.owner(), "If-Match", Long.toString(version(p)));
        assertEquals(200, cancel.status(), cancel.raw());
        assertEquals("CANCELLED", cancel.str("status"));
        assertEquals("Wrong supplier", cancel.str("cancelReason"));
        assertEquals(1, get("/api/v1/purchases/" + id + "/revisions", s.owner()).body().size());
        Integer rows = jdbc.queryForObject("select count(*) from purchase where id = uuid_to_bin(?)", Integer.class, id);
        assertEquals(1, rows, "still in the database");

        Resp draft = post("/api/v1/purchases", purchaseJson(sup, null, "2026-11-05", false, null), s.owner());
        Resp gone = deleteIfMatch("/api/v1/purchases/" + draft.str("id"), s.owner(), version(draft));
        assertEquals(200, gone.status(), gone.raw());
        assertTrue(gone.body().get("deleted").asBoolean());
    }

    @Test
    void permissionsAreCheckedByTheServer() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Bose");
        String dal = item(s.owner(), "Dal", KG, null, null);
        String line = newId();
        // Default staff: may record, enter prices and view history; may not edit completed purchases, cancel
        // them, or see expected profit.
        Resp p = post("/api/v1/purchases", purchaseJson(sup, "B-1", "2026-11-05", true, null,
                pline(line, dal, "10", KG, "PER_UNIT", "90", "110")), s.staff());
        assertEquals(201, p.status(), p.raw());
        assertEquals("900.00", p.str("totalCost"));
        assertNull(p.str("totalExpectedProfit"), "no EXPECTED_PROFIT_VIEW: profit fields removed");
        assertNull(p.str("marginPercent"));
        assertNull(field(lineOf(p, line), "expectedSales"));
        assertFalse(p.raw().contains("\"expectedProfit\":\"200.00\""));
        assertEquals("200.00", get("/api/v1/purchases/" + p.str("id"), s.owner()).str("totalExpectedProfit"));

        assertEquals(403, putIfMatch("/api/v1/purchases/" + p.str("id"), purchaseJson(sup, "B-1", "2026-11-05", true,
                null, pline(line, dal, "10", KG, "PER_UNIT", "80", "110")), s.staff(), version(p)).status());
        assertEquals(403, callWithHeader("POST", "/api/v1/purchases/" + p.str("id") + "/cancel",
                "{\"reason\":\"x\"}", s.staff(), "If-Match", Long.toString(version(p))).status());
        // Staff without SELLING_PRICE_ENTER on a new purchase, and the view-only user, are refused.
        assertEquals(403, post("/api/v1/purchases", purchaseJson(sup, "B-2", "2026-11-05", true, null,
                pline(newId(), dal, "1", KG, "PER_UNIT", "90", "110")), s.viewer()).status());
        assertEquals(403, get("/api/v1/purchases", s.viewer()).status());
        // Catalogue selling price is not changed by staff without CATALOG_SELLING_PRICE_CHANGE.
        assertEquals("90", get("/api/v1/items/" + dal, s.owner()).str("latestPurchasePrice"));
        assertNull(get("/api/v1/items/" + dal, s.owner()).str("latestSellingPrice"));
    }

    @Test
    void staffWithoutPriceRightsCannotEnterPrices() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Das");
        String dal = item(s.owner(), "Moong", KG, null, null);
        String u = unique("np");
        Resp c = createStaff(s.owner(), stepUp(s.owner(), PW), u, "[\"PURCHASE_RECORD\",\"PURCHASE_HISTORY_VIEW\"]");
        String noPrices = staffSession(u, c.str("temporaryPassword")).str("accessToken");
        Resp withPrice = post("/api/v1/purchases", purchaseJson(sup, "D-1", "2026-11-05", true, null,
                pline(newId(), dal, "5", KG, "PER_UNIT", "90", null)), noPrices);
        assertEquals(403, withPrice.status());
        Resp withSelling = post("/api/v1/purchases", purchaseJson(sup, "D-1", "2026-11-05", true, null,
                pline(newId(), dal, "5", KG, "PER_UNIT", null, "100")), noPrices);
        assertEquals(403, withSelling.status());
        assertEquals(201, post("/api/v1/purchases", purchaseJson(sup, "D-1", "2026-11-05", true, null,
                pline(newId(), dal, "5", KG, "PER_UNIT", null, null)), noPrices).status());
    }

    @Test
    void businessesAreIsolated() {
        Shop a = shop();
        Shop b = shop();
        String supA = supplier(a.owner(), "A Traders");
        String itemA = item(a.owner(), "A Item", PCS, null, null);
        Resp p = post("/api/v1/purchases", purchaseJson(supA, "X", "2026-11-05", true, null,
                pline(newId(), itemA, "1", PCS, "PER_UNIT", "10", "12")), a.owner());
        assertEquals(404, get("/api/v1/purchases/" + p.str("id"), b.owner()).status());
        assertEquals(0, get("/api/v1/purchases", b.owner()).body().size());
        // B cannot bill A's supplier or A's item, nor receive goods on A's purchase.
        String itemB = item(b.owner(), "B Item", PCS, null, null);
        assertEquals(400, post("/api/v1/purchases", purchaseJson(supA, "Y", "2026-11-05", true, null,
                pline(newId(), itemB, "1", PCS, "PER_UNIT", "1", null)), b.owner()).status());
        String supB = supplier(b.owner(), "B Traders");
        assertEquals(400, post("/api/v1/purchases", purchaseJson(supB, "Y", "2026-11-05", true, null,
                pline(newId(), itemA, "1", PCS, "PER_UNIT", "1", null)), b.owner()).status());
        String lineA = text(p.body().get("lines").get(0).get("id"));
        assertEquals(404, post("/api/v1/purchases/" + p.str("id") + "/deliveries", delivery(newId(), null,
                "2026-11-06", receipt(newId(), lineA, "1", null, null)), b.owner()).status());
    }

    @Test
    void aFailingPartIsNotSavedAtAll() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Atomic");
        String oil = item(s.owner(), "Ghee", KG, "500", "550");
        String id = newId();
        Resp bad = post("/api/v1/purchases?id=" + id, purchaseJson(sup, "AT-1", "2026-11-05", true,
                delivery(newId(), null, "2026-11-05", receipt(newId(), newId(), "2", null, null)),
                pline(newId(), oil, "2", KG, "PER_UNIT", "520", "560")), s.owner());
        assertEquals(400, bad.status(), bad.raw());
        assertEquals(404, get("/api/v1/purchases/" + id, s.owner()).status(), "no purchase without its delivery");
        assertEquals("500", get("/api/v1/items/" + oil, s.owner()).str("latestPurchasePrice"),
                "catalogue untouched when the save fails");
        Integer lines = jdbc.queryForObject("select count(*) from purchase_item where purchase_id = uuid_to_bin(?)",
                Integer.class, id);
        assertEquals(0, lines);
    }

    @Test
    void duplicateInvoiceIsAWarningAndSameIdIsNotSavedTwice() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Dup");
        String it = item(s.owner(), "Dup item", PCS, null, null);
        String id = newId();
        String body = purchaseJson(sup, "INV-77", "2026-11-05", true, null, pline(newId(), it, "1", PCS, "PER_UNIT",
                "10", null));
        assertEquals(201, post("/api/v1/purchases?id=" + id, body, s.owner()).status());
        Resp again = post("/api/v1/purchases?id=" + id, body, s.owner());
        assertEquals(409, again.status(), "same purchase id twice");
        Integer count = jdbc.queryForObject("select count(*) from purchase where id = uuid_to_bin(?)", Integer.class, id);
        assertEquals(1, count);

        Resp second = post("/api/v1/purchases", purchaseJson(sup, "inv-77", "2026-11-06", true, null,
                pline(newId(), it, "1", PCS, "PER_UNIT", "10", null)), s.owner());
        assertEquals(201, second.status());
        assertTrue(second.body().get("duplicateInvoice").asBoolean());
    }

    @Test
    void validation() {
        Shop s = shop();
        String sup = supplier(s.owner(), "Val");
        String it = item(s.owner(), "Val item", PCS, null, null);
        assertEquals(400, post("/api/v1/purchases", purchaseJson(sup, "V", "2026-11-05", true, null), s.owner()).status(),
                "a completed purchase needs items");
        assertEquals(400, post("/api/v1/purchases", purchaseJson(sup, "V", "2026-11-05", true, null,
                pline(newId(), it, "0", PCS, "PER_UNIT", "1", null)), s.owner()).status());
        assertEquals(400, post("/api/v1/purchases", purchaseJson(sup, "V", "2026-11-05", true, null,
                pline(newId(), it, "2", PCS, "TOTAL", "10.123", null)), s.owner()).status());
        assertEquals(400, post("/api/v1/purchases", purchaseJson(sup, "V", "2026-11-05", true, null,
                pline(newId(), it, "2", PCS, "PER_UNIT", "-1", null)), s.owner()).status());
        assertEquals(400, post("/api/v1/purchases", "{\"invoiceNo\":\"x\",\"purchaseDate\":\"2026-11-05\","
                + "\"complete\":false,\"lines\":[]}", s.owner()).status(), "supplier required");
    }
}
