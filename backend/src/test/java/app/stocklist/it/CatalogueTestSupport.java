package app.stocklist.it;

import java.util.UUID;

/** A business with an owner and two staff (default permissions / view-only), plus JSON helpers. */
abstract class CatalogueTestSupport extends IntegrationTestBase {
    static final String PW = "Green-Mango-42";
    static final String PCS = "00000000-0000-0000-0000-000000000001";
    static final String KG = "00000000-0000-0000-0000-000000000002";
    static final String LITRE = "00000000-0000-0000-0000-000000000004";

    record Shop(String owner, String staff, String viewer) {}

    /** owner; staff with default permissions; viewer with only ITEM_VIEW. Returns access tokens. */
    Shop shop() {
        String ownerUser = unique("own");
        String owner = registerBusiness(ownerUser, PW).str("accessToken");
        String s1 = unique("st");
        Resp c1 = createStaff(owner, stepUp(owner, PW), s1, null);
        String staff = staffSession(s1, c1.str("temporaryPassword")).str("accessToken");
        String s2 = unique("vw");
        Resp c2 = createStaff(owner, stepUp(owner, PW), s2, "[\"ITEM_VIEW\"]");
        String viewer = staffSession(s2, c2.str("temporaryPassword")).str("accessToken");
        return new Shop(owner, staff, viewer);
    }

    static String supplierJson(String name) {
        return """
                {"name":"%s","contactPerson":"Ramesh","mobile":"9876543210","city":"Pune","notes":"Delivers Mondays"}
                """.formatted(name);
    }

    static String itemJson(String name, String unit, String buy, String sell, String supplierId) {
        return """
                {"name":"%s","category":"Oil","defaultUnitId":"%s","latestPurchasePrice":%s,"latestSellingPrice":%s,
                 "preferredSupplierId":%s}
                """.formatted(name, unit, q(buy), q(sell), q(supplierId));
    }

    static String line(String id, String name, String qty, String unit, String basis, String price) {
        return """
                {"id":"%s","itemName":"%s","quantity":"%s","unitId":"%s","priceBasis":"%s","enteredPrice":%s}
                """.formatted(id, name, qty, unit, basis, q(price));
    }

    static String listJson(String title, String status, String supplierId, String... lines) {
        return """
                {"title":"%s","listDate":"2026-11-14","supplierId":%s,"notes":"Urgent","status":"%s","lines":[%s]}
                """.formatted(title, q(supplierId), status, String.join(",", lines));
    }

    static String q(String v) {
        return v == null ? "null" : "\"" + v + "\"";
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    Resp putIfMatch(String path, String json, String token, long version) {
        return callWithHeader("PUT", path, json, token, "If-Match", Long.toString(version));
    }

    Resp deleteIfMatch(String path, String token, long version) {
        return callWithHeader("DELETE", path, null, token, "If-Match", Long.toString(version));
    }

    static long version(Resp r) {
        return Long.parseLong(text(r.body().get("version")));
    }
}
