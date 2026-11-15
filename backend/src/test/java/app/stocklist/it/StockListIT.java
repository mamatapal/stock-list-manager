package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class StockListIT extends CatalogueTestSupport {

    private static JsonNode lineNamed(Resp list, String name) {
        for (JsonNode l : list.body().get("lines")) {
            if (name.equals(text(l.get("itemName")))) return l;
        }
        throw new AssertionError("no line " + name + " in " + list.raw());
    }

    @Test
    void priceBasisTotalsAreNeverMultipliedTwice() {
        Shop s = shop();
        Resp list = post("/api/v1/stock-lists", listJson("Weekly order", "SAVED", null,
                line(newId(), "Sunflower Oil", "20", LITRE, "PER_UNIT", "140"),
                line(newId(), "Basmati Rice", "1.5", KG, "TOTAL", "270"),
                line(newId(), "Milk", "2.75", LITRE, "PER_UNIT", "56.40"),
                line(newId(), "Matchboxes", "10", PCS, "PER_UNIT", null)), s.owner());
        assertEquals(201, list.status(), list.raw());

        assertEquals("2800.00", text(lineNamed(list, "Sunflower Oil").get("lineTotal")));
        JsonNode rice = lineNamed(list, "Basmati Rice");
        assertEquals("270.00", text(rice.get("lineTotal")), "TOTAL amount is used as entered, not x 1.5");
        assertEquals("180", text(rice.get("effectiveUnitPrice")));
        assertEquals("155.10", text(lineNamed(list, "Milk").get("lineTotal")), "2.75 x 56.40");
        assertNull(text(lineNamed(list, "Matchboxes").get("lineTotal")), "no price, no total");
        assertEquals("3225.10", list.str("totalAmount"));
        assertEquals("2.75", text(lineNamed(list, "Milk").get("quantity")));
    }

    @Test
    void validation() {
        Shop s = shop();
        Resp empty = post("/api/v1/stock-lists", listJson("Empty", "SAVED", null), s.owner());
        assertEquals(400, empty.status());
        assertEquals("EMPTY_LIST", empty.str("code"));
        assertEquals(201, post("/api/v1/stock-lists", listJson("Empty draft", "DRAFT", null), s.owner()).status());
        assertEquals(400, post("/api/v1/stock-lists", listJson("Zero", "SAVED", null,
                line(newId(), "X", "0", PCS, "PER_UNIT", "1")), s.owner()).status(), "quantity must be > 0");
        assertEquals(400, post("/api/v1/stock-lists", listJson("Q", "SAVED", null,
                line(newId(), "X", "1.2345", KG, "PER_UNIT", "1")), s.owner()).status(), "3 decimals max");
        assertEquals(400, post("/api/v1/stock-lists", listJson("T", "SAVED", null,
                line(newId(), "X", "2", PCS, "TOTAL", "10.555")), s.owner()).status(), "total amount 2 decimals");
        assertEquals(400, post("/api/v1/stock-lists", listJson("B", "SAVED", null,
                line(newId(), "X", "2", PCS, "WHOLE", "10")), s.owner()).status(), "unknown basis");
        assertEquals(400, post("/api/v1/stock-lists", listJson("N", "SAVED", null,
                line(newId(), "", "2", PCS, "PER_UNIT", "10")), s.owner()).status(), "line needs a name");
        assertEquals(400, post("/api/v1/stock-lists", "{\"title\":\"\",\"listDate\":\"2026-11-14\",\"status\":\"DRAFT\","
                + "\"lines\":[]}", s.owner()).status(), "title required");
    }

    @Test
    void editLinesDraftToSavedAndConflicts() {
        Shop s = shop();
        String keep = newId(), drop = newId();
        Resp draft = post("/api/v1/stock-lists", listJson("Monday", "DRAFT", null,
                line(keep, "Sugar", "5", KG, "PER_UNIT", "44"),
                line(drop, "Salt", "10", PCS, "PER_UNIT", "20")), s.staff());
        assertEquals(201, draft.status(), draft.raw());
        String id = draft.str("id");
        assertEquals("420.00", draft.str("totalAmount"));

        Resp saved = putIfMatch("/api/v1/stock-lists/" + id, listJson("Monday", "SAVED", null,
                line(keep, "Sugar", "7.5", KG, "PER_UNIT", "44"),
                line(newId(), "Tea", "1", PCS, "TOTAL", "250")), s.staff(), 0);
        assertEquals(200, saved.status(), saved.raw());
        assertEquals("SAVED", saved.str("status"));
        assertEquals(2, saved.body().get("lines").size(), "Salt removed, Tea added");
        assertEquals("330.00", text(lineNamed(saved, "Sugar").get("lineTotal")));
        assertEquals("580.00", saved.str("totalAmount"));
        Integer saltRows = jdbc.queryForObject("select count(*) from stock_list_item where id = uuid_to_bin(?)",
                Integer.class, drop);
        assertEquals(0, saltRows);

        Resp stale = putIfMatch("/api/v1/stock-lists/" + id, listJson("Monday v0", "SAVED", null,
                line(keep, "Sugar", "1", KG, "PER_UNIT", "44")), s.owner(), 0);
        assertEquals(409, stale.status(), "someone else saved version 1 meanwhile");
        assertEquals("Monday", get("/api/v1/stock-lists/" + id, s.owner()).str("title"), "nothing overwritten");
    }

    @Test
    void duplicateSearchFilterAndDelete() {
        Shop s = shop();
        String sup = post("/api/v1/suppliers", supplierJson(unique("Gupta ")), s.owner()).str("id");
        Resp a = post("/api/v1/stock-lists", listJson("Dairy order", "SAVED", sup,
                line(newId(), "Paneer", "2", KG, "PER_UNIT", "380")), s.owner());
        post("/api/v1/stock-lists", listJson("Snacks", "DRAFT", null,
                line(newId(), "Biscuits", "24", PCS, "PER_UNIT", "10")), s.owner());

        Resp copy = post("/api/v1/stock-lists/" + a.str("id") + "/duplicate", null, s.owner());
        assertEquals(201, copy.status(), copy.raw());
        assertEquals("Copy of Dairy order", copy.str("title"));
        assertEquals("DRAFT", copy.str("status"));
        assertEquals("760.00", copy.str("totalAmount"));
        assertNotEquals(text(a.body().get("lines").get(0).get("id")), text(copy.body().get("lines").get(0).get("id")));

        assertEquals(1, get("/api/v1/stock-lists?q=paneer&status=SAVED", s.owner()).body().size(), "by item name");
        assertEquals(2, get("/api/v1/stock-lists?supplierId=" + sup, s.owner()).body().size());
        assertEquals(1, get("/api/v1/stock-lists?q=snack", s.owner()).body().size());
        int inNov = get("/api/v1/stock-lists?from=2026-11-14&to=2026-11-14", s.owner()).body().size();
        assertTrue(inNov >= 2, "the two lists dated 14-11-2026");
        assertEquals(0, get("/api/v1/stock-lists?from=2020-01-01&to=2020-12-31", s.owner()).body().size());

        assertEquals(200, deleteIfMatch("/api/v1/stock-lists/" + a.str("id"), s.owner(), 0).status());
        assertEquals(0, get("/api/v1/stock-lists?q=dairy%20order&status=SAVED", s.owner()).body().size());
    }

    @Test
    void listPermissions() {
        Shop s = shop();
        assertEquals(403, get("/api/v1/stock-lists", s.viewer()).status(), "ITEM_VIEW alone cannot see lists");
        assertEquals(403, post("/api/v1/stock-lists", listJson("X", "DRAFT", null), s.viewer()).status());
        Resp mine = post("/api/v1/stock-lists", listJson("Staff list", "DRAFT", null), s.staff());
        assertEquals(201, mine.status());
        assertEquals(403, deleteIfMatch("/api/v1/stock-lists/" + mine.str("id"), s.staff(), 0).status(),
                "delete is off by default for staff");
        assertEquals(200, get("/api/v1/stock-lists/" + mine.str("id"), s.owner()).status(), "owner sees staff's list");
    }

    @Test
    void anotherBusinessCannotReachLists() {
        Shop a = shop();
        Shop b = shop();
        String sup = post("/api/v1/suppliers", supplierJson(unique("A ")), a.owner()).str("id");
        String item = post("/api/v1/items", itemJson(unique("A item "), PCS, null, null, null), a.owner()).str("id");
        Resp list = post("/api/v1/stock-lists", listJson("A private", "SAVED", sup,
                line(newId(), "Ghee", "1", KG, "PER_UNIT", "600")), a.owner());
        String id = list.str("id");

        assertEquals(404, get("/api/v1/stock-lists/" + id, b.owner()).status());
        assertEquals(404, putIfMatch("/api/v1/stock-lists/" + id, listJson("Hijack", "DRAFT", null), b.owner(), 0)
                .status());
        assertEquals(404, deleteIfMatch("/api/v1/stock-lists/" + id, b.owner(), 0).status());
        assertEquals(404, post("/api/v1/stock-lists/" + id + "/duplicate", null, b.owner()).status());
        assertEquals(0, get("/api/v1/stock-lists", b.owner()).body().size());
        assertEquals(400, post("/api/v1/stock-lists", listJson("B", "DRAFT", sup), b.owner()).status(),
                "A's supplier");
        String lineWithAItem = """
                {"id":"%s","itemId":"%s","quantity":"1","unitId":"%s","priceBasis":"PER_UNIT"}"""
                .formatted(newId(), item, PCS);
        assertEquals(400, post("/api/v1/stock-lists", listJson("B", "DRAFT", null, lineWithAItem), b.owner()).status(),
                "A's item");
        String reusedLineId = text(list.body().get("lines").get(0).get("id"));
        assertEquals(400, post("/api/v1/stock-lists", listJson("B", "DRAFT", null,
                line(reusedLineId, "Steal", "1", PCS, "PER_UNIT", "1")), b.owner()).status(), "A's line id");
        assertTrue(get("/api/v1/stock-lists/" + id, a.owner()).raw().contains("Ghee"), "A's list untouched");
    }
}
