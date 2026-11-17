package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import tools.jackson.databind.JsonNode;

/** Helpers for the supplier network tests: approved suppliers, connections, products, links and orders. */
abstract class NetworkTestSupport extends SupplierTestSupport {
    static final String NET = "/api/v1/supplier-network";
    static final String ADMIN_API = "/api/v1/admin";

    /** An approved supplier: its access token, organisation id and admin session (for suspensions). */
    record Sup(String token, String orgId, String email, String adminCookie) {}

    /** Registers (with a chosen business name), verifies the email and signs in; waiting for approval. */
    Resp verifiedSupplierNamed(String email, String legalName) {
        Resp reg = post(PORTAL + "/auth/register", registerJson(email, legalName, "9876543210", null, SUPPLIER_PW));
        assertEquals(202, reg.status(), reg.raw());
        assertEquals(200, verify(token(supplierMail.verifyLinks.get(email))).status());
        Resp s = supplierLogin(email, SUPPLIER_PW);
        assertEquals(200, s.status(), s.raw());
        return s;
    }

    Sup approvedSupplier(boolean listed) {
        return approvedSupplier(listed, "Network Wholesale " + unique(""));
    }

    Sup approvedSupplier(boolean listed, String legalName) {
        String email = supplierEmail();
        Resp session = verifiedSupplierNamed(email, legalName);
        String org = orgId(session);
        Admin admin = newAdmin();
        String cookie = signIn(admin);
        Http approved = adminPost(ADMIN_API + "/suppliers/" + org + "/approve", null, cookie, orgVersion(cookie, org));
        assertEquals(200, approved.status(), approved.raw());
        Sup s = new Sup(session.str("accessToken"), org, email, cookie);
        if (listed) setListed(s, true);
        return s;
    }

    Resp setListed(Sup s, boolean listed) {
        Resp r = put(PORTAL + "/directory-listing", "{\"listed\":" + listed + "}", s.token(), null);
        assertEquals(200, r.status(), r.raw());
        return r;
    }

    Http suspend(Sup s) {
        Http r = adminPost(ADMIN_API + "/suppliers/" + s.orgId() + "/suspend", "{\"reason\":\"Checks pending\"}",
                s.adminCookie(), orgVersion(s.adminCookie(), s.orgId()));
        assertEquals(200, r.status(), r.raw());
        return r;
    }

    Http reinstate(Sup s) {
        Http r = adminPost(ADMIN_API + "/suppliers/" + s.orgId() + "/reinstate", null, s.adminCookie(),
                orgVersion(s.adminCookie(), s.orgId()));
        assertEquals(200, r.status(), r.raw());
        return r;
    }

    /** The shop asks; returns the connection id. */
    String request(String shopToken, Sup s) {
        Resp r = post(NET + "/connections", "{\"supplierOrgId\":\"" + s.orgId() + "\"}", shopToken);
        assertEquals(201, r.status(), r.raw());
        return r.str("id");
    }

    /** Request + accept; returns the connection id. */
    String connect(String shopToken, Sup s) {
        String id = request(shopToken, s);
        Resp a = post(PORTAL + "/connections/" + id + "/accept", null, s.token());
        assertEquals(200, a.status(), a.raw());
        return id;
    }

    String product(Sup s, String nameEn, String unit, String price) {
        Resp r = post(PORTAL + "/products", productJson(nameEn, unit, price), s.token());
        assertEquals(201, r.status(), r.raw());
        return r.str("id");
    }

    static String productJson(String nameEn, String unit, String price) {
        return """
                {"nameEn":"%s","nameHi":"%s (हिंदी)","unit":"%s","pack":"1 %s","price":"%s"}
                """.formatted(nameEn, nameEn, unit, unit, price);
    }

    Resp link(String shopToken, String connectionId, String productId, String itemId, String unitId, String perPack) {
        return put(NET + "/connections/" + connectionId + "/products/" + productId + "/link", """
                {"itemId":"%s","unitId":%s,"unitsPerPack":%s}""".formatted(itemId, q(unitId), q(perPack)),
                shopToken, null);
    }

    void linkContact(String shopToken, String connectionId, String supplierContactId) {
        Resp r = put(NET + "/connections/" + connectionId + "/contact", "{\"supplierId\":\"" + supplierContactId + "\"}",
                shopToken, null);
        assertEquals(200, r.status(), r.raw());
    }

    static String orderLine(String productId, String qty) {
        return "{\"productId\":\"" + productId + "\",\"quantity\":\"" + qty + "\"}";
    }

    static String orderJson(String id, String connectionId, String... lines) {
        return """
                {"id":%s,"connectionId":"%s","note":"Deliver before Monday","lines":[%s]}
                """.formatted(q(id), connectionId, String.join(",", lines));
    }

    Resp order(String shopToken, String connectionId, String... lines) {
        Resp r = post(NET + "/orders", orderJson(null, connectionId, lines), shopToken);
        assertEquals(201, r.status(), r.raw());
        return r;
    }

    Resp supplierAct(Sup s, String orderId, String action, String body) {
        return post(PORTAL + "/orders/" + orderId + "/" + action, body, s.token());
    }

    static String receiptLine(String lineId, String qty, String price, String batch, String expiry) {
        return """
                {"lineId":"%s","quantity":"%s","unitPrice":%s,"batchNo":%s,"expiryDate":%s}
                """.formatted(lineId, qty, q(price), q(batch), q(expiry));
    }

    static String receiptJson(String date, String invoice, String... lines) {
        return """
                {"receivedDate":%s,"invoiceNo":%s,"notes":"Checked at the counter","lines":[%s]}
                """.formatted(q(date), q(invoice), String.join(",", lines));
    }

    /** The id of the order line for a product. */
    static String lineFor(Resp order, String productId) {
        for (JsonNode l : order.body().get("lines")) {
            if (productId.equals(text(l.get("productId")))) return text(l.get("id"));
        }
        throw new AssertionError("no line for " + productId + " in " + order.raw());
    }

    /** Number of batches of an item, read directly (stock = batches from confirmed deliveries). */
    long batchCount(String itemId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM batch WHERE item_id = UUID_TO_BIN(?)", Long.class, itemId);
    }

    /** Purchases, deliveries and batches recorded for a purchase id (the id an order reserves for its receipt). */
    long[] receiptRows(String purchaseId) {
        return new long[]{
                jdbc.queryForObject("SELECT COUNT(*) FROM purchase WHERE id = UUID_TO_BIN(?)", Long.class, purchaseId),
                jdbc.queryForObject("SELECT COUNT(*) FROM purchase_delivery WHERE purchase_id = UUID_TO_BIN(?)",
                        Long.class, purchaseId),
                jdbc.queryForObject("SELECT COUNT(*) FROM batch WHERE purchase_id = UUID_TO_BIN(?)", Long.class,
                        purchaseId)};
    }
}
