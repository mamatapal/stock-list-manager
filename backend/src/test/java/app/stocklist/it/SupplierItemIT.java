package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class SupplierItemIT extends CatalogueTestSupport {

    private static boolean containsName(Resp list, String name) {
        for (JsonNode n : list.body()) {
            if (name.equals(text(n.get("name")))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------- suppliers

    @Test
    void supplierCrudWithSoftDelete() {
        Shop s = shop();
        String name = unique("Sharma Traders ");
        Resp created = post("/api/v1/suppliers", supplierJson(name), s.owner());
        assertEquals(201, created.status(), created.raw());
        String id = created.str("id");
        assertEquals(0, version(created));

        assertEquals(name, get("/api/v1/suppliers/" + id, s.owner()).str("name"));
        assertTrue(containsName(get("/api/v1/suppliers?q=sharma", s.owner()), name), "search by name");
        assertTrue(containsName(get("/api/v1/suppliers?q=pune", s.owner()), name), "search by city");

        Resp updated = putIfMatch("/api/v1/suppliers/" + id, """
                {"name":"%s","mobile":"9876500000","active":false}""".formatted(name), s.owner(), 0);
        assertEquals(200, updated.status(), updated.raw());
        assertEquals(1, version(updated));
        assertFalse(containsName(get("/api/v1/suppliers", s.owner()), name), "inactive hidden by default");
        assertTrue(containsName(get("/api/v1/suppliers?includeInactive=true", s.owner()), name));

        Resp stale = putIfMatch("/api/v1/suppliers/" + id, supplierJson(name), s.owner(), 0);
        assertEquals(409, stale.status());
        assertEquals("VERSION_CONFLICT", stale.str("code"));
        assertEquals("1", text(stale.body().get("currentVersion")));

        Resp deleted = deleteIfMatch("/api/v1/suppliers/" + id, s.owner(), 1);
        assertEquals(200, deleted.status(), deleted.raw());
        assertEquals("true", text(deleted.body().get("deleted")));
        assertFalse(containsName(get("/api/v1/suppliers?includeInactive=true", s.owner()), name));
        Integer rows = jdbc.queryForObject("select count(*) from supplier where id = uuid_to_bin(?)", Integer.class, id);
        assertEquals(1, rows, "never hard-deleted");
    }

    @Test
    void supplierValidation() {
        Shop s = shop();
        Resp noName = post("/api/v1/suppliers", "{\"name\":\"\"}", s.owner());
        assertEquals(400, noName.status());
        Resp badPhone = post("/api/v1/suppliers", "{\"name\":\"X\",\"mobile\":\"12ab\"}", s.owner());
        assertEquals(400, badPhone.status());
        Resp badGst = post("/api/v1/suppliers", "{\"name\":\"X\",\"gstin\":\"123\"}", s.owner());
        assertEquals(400, badGst.status());
    }

    @Test
    void supplierPermissions() {
        Shop s = shop();
        Resp byStaff = post("/api/v1/suppliers", supplierJson(unique("S")), s.staff());
        assertEquals(201, byStaff.status(), "staff may add suppliers by default");
        String id = byStaff.str("id");
        assertEquals(200, putIfMatch("/api/v1/suppliers/" + id, supplierJson("Renamed"), s.staff(), 0).status());
        assertEquals(403, deleteIfMatch("/api/v1/suppliers/" + id, s.staff(), 1).status(), "delete is off by default");
        assertEquals(403, get("/api/v1/suppliers", s.viewer()).status(), "no SUPPLIER_VIEW");
        assertEquals(403, post("/api/v1/suppliers", supplierJson("Nope"), s.viewer()).status());
    }

    // ------------------------------------------------------------- items

    @Test
    void itemCrudCategoriesAndDuplicateNames() {
        Shop s = shop();
        String sup = post("/api/v1/suppliers", supplierJson(unique("S")), s.owner()).str("id");
        Resp oil = post("/api/v1/items", itemJson("Sunflower Oil 1L", LITRE, "140", "165", sup), s.owner());
        assertEquals(201, oil.status(), oil.raw());
        assertEquals("140", oil.str("latestPurchasePrice"));
        assertEquals("165", oil.str("latestSellingPrice"));
        assertEquals(sup, oil.str("preferredSupplierId"));

        Resp dup = post("/api/v1/items", itemJson("sunflower oil 1l", PCS, null, null, null), s.owner());
        assertEquals(409, dup.status());
        assertEquals("DUPLICATE_ITEM_NAME", dup.str("code"));

        Resp cats = get("/api/v1/items/categories", s.owner());
        assertTrue(cats.raw().contains("Oil"));
        assertEquals(1, get("/api/v1/items?category=Oil", s.owner()).body().size());
        assertEquals(1, get("/api/v1/items?q=sunflower", s.owner()).body().size());

        Resp upd = putIfMatch("/api/v1/items/" + oil.str("id"),
                itemJson("Sunflower Oil 1L", LITRE, "142.5", "165", sup), s.owner(), 0);
        assertEquals(200, upd.status(), upd.raw());
        assertEquals("142.5", upd.str("latestPurchasePrice"));

        assertEquals(200, deleteIfMatch("/api/v1/items/" + oil.str("id"), s.owner(), 1).status());
        assertEquals(201, post("/api/v1/items", itemJson("Sunflower Oil 1L", LITRE, null, null, null), s.owner())
                .status(), "a deleted item's name can be reused");
    }

    @Test
    void itemValidation() {
        Shop s = shop();
        assertEquals(400, post("/api/v1/items", itemJson("A", "00000000-0000-0000-0000-0000000000ff", null, null,
                null), s.owner()).status(), "unknown unit");
        assertEquals(400, post("/api/v1/items", itemJson("B", PCS, "-5", null, null), s.owner()).status());
        assertEquals(400, post("/api/v1/items", itemJson("C", PCS, "1.23456", null, null), s.owner()).status());
        assertEquals(400, post("/api/v1/items", itemJson("D", PCS, "abc", null, null), s.owner()).status());
        assertEquals(400, post("/api/v1/items", "{\"name\":\"E\"}", s.owner()).status(), "unit required");
    }

    @Test
    void sellingPricePermissions() {
        Shop s = shop();
        Resp created = post("/api/v1/items", itemJson(unique("Rice "), KG, "40", "48", null), s.staff());
        assertEquals(201, created.status(), "staff may enter prices on a new item: " + created.raw());
        String id = created.str("id");
        Resp changePrice = putIfMatch("/api/v1/items/" + id, itemJson(text(created.body().get("name")), KG, "40", "50",
                null), s.staff(), 0);
        assertEquals(403, changePrice.status(), "changing the catalogue selling price needs its own permission");
        Resp otherEdit = putIfMatch("/api/v1/items/" + id, itemJson(text(created.body().get("name")), KG, "41", "48",
                null), s.staff(), 0);
        assertEquals(200, otherEdit.status(), otherEdit.raw());
        assertEquals(200, putIfMatch("/api/v1/items/" + id, itemJson(text(created.body().get("name")), KG, "41", "50",
                null), s.owner(), 1).status(), "the owner may");
        Integer logged = jdbc.queryForObject("select count(*) from activity_log where action = 'SELLING_PRICE_CHANGED' "
                + "and record_id = ?", Integer.class, id);
        assertEquals(1, logged);
        assertEquals(403, post("/api/v1/items", itemJson("X", PCS, null, null, null), s.viewer()).status());
        assertEquals(200, get("/api/v1/items", s.viewer()).status());
    }

    @Test
    void customUnits() {
        Shop s = shop();
        Resp tray = post("/api/v1/units", "{\"code\":\"tray\",\"name\":\"Tray of 30\"}", s.owner());
        assertEquals(201, tray.status(), tray.raw());
        assertEquals("TRAY", tray.str("code"));
        Resp units = get("/api/v1/units", s.owner());
        assertEquals(15, units.body().size(), "14 built-in + 1 custom");
        assertEquals(409, post("/api/v1/units", "{\"code\":\"TRAY\",\"name\":\"Again\"}", s.owner()).status());
        assertEquals(201, post("/api/v1/items", itemJson("Eggs", tray.str("id"), "180", null, null), s.owner()).status());
    }

    // ------------------------------------------------------------- isolation

    @Test
    void anotherBusinessCannotReachSuppliersOrItems() {
        Shop a = shop();
        Shop b = shop();
        String sup = post("/api/v1/suppliers", supplierJson(unique("A-sup ")), a.owner()).str("id");
        String item = post("/api/v1/items", itemJson(unique("A-item "), PCS, "10", null, null), a.owner()).str("id");
        String tray = post("/api/v1/units", "{\"code\":\"crate\",\"name\":\"Crate\"}", a.owner()).str("id");

        assertEquals(404, get("/api/v1/suppliers/" + sup, b.owner()).status());
        assertEquals(404, putIfMatch("/api/v1/suppliers/" + sup, supplierJson("Hijack"), b.owner(), 0).status());
        assertEquals(404, deleteIfMatch("/api/v1/suppliers/" + sup, b.owner(), 0).status());
        assertEquals(404, get("/api/v1/items/" + item, b.owner()).status());
        assertEquals(404, putIfMatch("/api/v1/items/" + item, itemJson("Hijack", PCS, null, null, null), b.owner(), 0)
                .status());
        assertEquals(0, get("/api/v1/suppliers?includeInactive=true", b.owner()).body().size());
        assertEquals(0, get("/api/v1/items?includeInactive=true", b.owner()).body().size());
        assertEquals(14, get("/api/v1/units", b.owner()).body().size(), "A's custom unit is invisible to B");

        assertEquals(400, post("/api/v1/items", itemJson("B item", PCS, null, null, sup), b.owner()).status(),
                "cannot point at A's supplier");
        assertEquals(400, post("/api/v1/items", itemJson("B item", tray, null, null, null), b.owner()).status(),
                "cannot use A's custom unit");
        assertEquals(409, post("/api/v1/suppliers?id=" + sup, supplierJson("Same id"), b.owner()).status(),
                "cannot take over an id used by another business");
    }
}
