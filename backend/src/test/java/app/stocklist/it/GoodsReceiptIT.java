package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import tools.jackson.databind.JsonNode;

/**
 * Goods receipt for purchase orders: only the receipt changes stock, and it does so through the existing purchase
 * workflow (PurchaseService: completed purchase, one delivery, batches), once, atomically.
 */
class GoodsReceiptIT extends NetworkTestSupport {

    /** A shop connected to a supplier, contact linked, two linked products (box of 24 pcs; litre). */
    record World(Shop shop, Sup sup, String conn, String contact, String biscuits, String oil, String boxProduct,
                 String oilProduct) {}

    private World world() {
        Shop shop = shop();
        Sup s = approvedSupplier(true);
        String conn = connect(shop.owner(), s);
        String contact = supplier(shop.owner(), "Agarwal (order supplier)");
        linkContact(shop.owner(), conn, contact);
        String biscuits = item(shop.owner(), "Glucose biscuits", PCS, "10", "12");
        String oil = item(shop.owner(), "Mustard oil", LITRE, "150", "170");
        String box = product(s, "Glucose biscuits", "box", "240");
        String oilP = product(s, "Mustard oil", "litre", "140");
        assertEquals(200, link(shop.owner(), conn, box, biscuits, PCS, "24").status());
        assertEquals(200, link(shop.owner(), conn, oilP, oil, LITRE, "1").status());
        return new World(shop, s, conn, contact, biscuits, oil, box, oilP);
    }

    private Resp placeAndAccept(World w, String boxes, String litres) {
        Resp o = order(w.shop().owner(), w.conn(), orderLine(w.boxProduct(), boxes), orderLine(w.oilProduct(), litres));
        assertEquals("ACCEPTED", supplierAct(w.sup(), o.str("id"), "accept", null).str("status"));
        return get(NET + "/orders/" + o.str("id"), w.shop().owner());
    }

    private static void assertMoney(String expected, String actual, String why) {
        assertEquals(0, new java.math.BigDecimal(expected).compareTo(new java.math.BigDecimal(actual)),
                why + ": expected " + expected + " but was " + actual);
    }

    private String receiptPurchaseId(String orderId) {
        return jdbc.queryForObject("SELECT BIN_TO_UUID(receipt_purchase_id) FROM purchase_order WHERE id = UUID_TO_BIN(?)",
                String.class, orderId);
    }

    private String fullReceipt(Resp order, World w, String boxes, String litres) {
        return receiptJson("2026-11-16", "INV-77",
                receiptLine(lineFor(order, w.boxProduct()), boxes, null, "B-7", "2027-03-31"),
                receiptLine(lineFor(order, w.oilProduct()), litres, null, null, null));
    }

    @Test
    void stockChangesOnlyOnReceiptThroughOneCompletedPurchaseWithOneDelivery() {
        World w = world();
        Resp order = placeAndAccept(w, "2", "5");
        String id = order.str("id");
        String rp = receiptPurchaseId(id);
        assertEquals(200, supplierAct(w.sup(), id, "dispatch", null).status());
        assertEquals(0, batchCount(w.biscuits()), "accepting and dispatching do not touch stock");
        assertEquals(0, batchCount(w.oil()));
        assertArrayEquals(new long[]{0, 0, 0}, receiptRows(rp));

        // 2 boxes of 24 pcs; 4 of the 5 litres arrived.
        Resp r = post(NET + "/orders/" + id + "/receive", fullReceipt(order, w, "2", "4"), w.shop().owner());
        assertEquals(200, r.status(), r.raw());
        assertEquals("RECEIVED", text(r.at("/order/status")));
        assertEquals(rp, r.str("purchaseId"));
        assertEquals(rp, text(r.at("/order/purchaseId")));
        assertArrayEquals(new long[]{1, 1, 2}, receiptRows(rp), "one purchase, one delivery, one batch per line");

        // It is an ordinary purchase of this shop, made by the existing purchase workflow.
        Resp p = get("/api/v1/purchases/" + rp, w.shop().owner());
        assertEquals(200, p.status(), p.raw());
        assertEquals(w.contact(), p.str("supplierId"));
        assertEquals("INV-77", p.str("invoiceNo"));
        assertEquals("2026-11-16", p.str("purchaseDate"));
        assertEquals("RECEIVED", p.str("status"), p.raw());
        assertEquals(1, p.body().get("deliveries").size());
        assertEquals(2, p.body().get("lines").size());
        JsonNode biscuitsLine = null, oilLine = null;
        for (JsonNode l : p.body().get("lines")) {
            if (w.biscuits().equals(text(l.get("itemId")))) biscuitsLine = l;
            if (w.oil().equals(text(l.get("itemId")))) oilLine = l;
        }
        assertEquals("48", text(biscuitsLine.get("quantity")), "converted with the link: 2 x 24 pcs");
        assertEquals(PCS, text(biscuitsLine.get("unitId")));
        assertMoney("480", text(biscuitsLine.get("purchasePrice")), "saved order price: 2 x 240");
        assertEquals("4", text(oilLine.get("quantity")), "the actual quantity received");
        assertMoney("560", text(oilLine.get("purchasePrice")), "4 x 140");

        Resp batches = get("/api/v1/batches?itemId=" + w.biscuits(), w.shop().owner());
        assertEquals(1, batches.body().size(), batches.raw());
        assertEquals("48", text(batches.body().get(0).get("quantity")));
        assertEquals("2027-03-31", text(batches.body().get(0).get("expiryDate")));
        assertEquals("B-7", text(batches.body().get(0).get("batchNo")));

        Resp after = get(NET + "/orders/" + id, w.shop().owner());
        assertEquals("2", text(after.body().get("lines").get(0).get("receivedQuantity")));
        assertMoney("240", text(after.body().get("lines").get(0).get("receivedUnitPrice")), "saved price kept on the order");
        assertEquals("4", text(after.body().get("lines").get(1).get("receivedQuantity")));
        JsonNode history = after.body().get("history");
        assertEquals("RECEIVED", text(history.get(history.size() - 1).get("toStatus")));
        assertEquals("SHORT", text(history.get(history.size() - 1).get("receiptStatus")), "4 of 5 litres: short");
        assertEquals("FULL", text(after.at("/lines/0/receiptStatus")));
        assertEquals("SHORT", text(after.at("/lines/1/receiptStatus")));
        assertEquals("1", text(after.at("/lines/1/shortageQuantity")));
        assertEquals("SHOP", text(history.get(history.size() - 1).get("actorType")));
        assertEquals("RECEIVED", get(PORTAL + "/orders/" + id, w.sup().token()).str("status"));
    }

    @Test
    void aSecondReceiptIsRefusedAndChangesNothing() {
        World w = world();
        Resp order = placeAndAccept(w, "1", "1");
        String id = order.str("id");
        String rp = receiptPurchaseId(id);
        assertEquals(200, post(NET + "/orders/" + id + "/receive", fullReceipt(order, w, "1", "1"), w.shop().owner()).status());
        Resp again = post(NET + "/orders/" + id + "/receive", fullReceipt(order, w, "1", "1"), w.shop().owner());
        assertEquals(409, again.status(), again.raw());
        assertEquals("ORDER_ALREADY_RECEIVED", again.str("code"));
        assertArrayEquals(new long[]{1, 1, 2}, receiptRows(rp));
        assertEquals(1, batchCount(w.biscuits()));
        assertEquals("ORDER_STATUS", post(NET + "/orders/" + id + "/cancel", null, w.shop().owner()).str("code"));
    }

    @Test
    void concurrentReceiptsCreateTheStockOnce() throws Exception {
        World w = world();
        Resp order = placeAndAccept(w, "3", "2");
        String id = order.str("id");
        String body = fullReceipt(order, w, "3", "2");
        int n = 4;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Resp>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return post(NET + "/orders/" + id + "/receive", body, w.shop().owner());
            }));
        }
        start.countDown();
        int ok = 0, refused = 0;
        for (Future<Resp> f : results) {
            Resp r = f.get(60, TimeUnit.SECONDS);
            if (r.status() == 200) ok++;
            else if (r.status() == 409) refused++;
            else throw new AssertionError("unexpected answer " + r.raw());
        }
        pool.shutdown();
        assertEquals(1, ok, "exactly one receipt succeeds");
        assertEquals(n - 1, refused);
        assertArrayEquals(new long[]{1, 1, 2}, receiptRows(receiptPurchaseId(id)));
        assertEquals(1, batchCount(w.biscuits()));
        assertEquals(1, batchCount(w.oil()));
    }

    @Test
    void aFailureAtTheLastStepRollsTheWholeReceiptBack() {
        World w = world();
        Resp first = placeAndAccept(w, "2", "2");
        Resp second = placeAndAccept(w, "1", "1");
        String id = first.str("id");
        String rp = receiptPurchaseId(id);
        // Force the final step to fail: another order already claims the purchase id this receipt will create, so
        // linking it (unique purchase_order.purchase_id) fails after the purchase, delivery and batches were written.
        claimPurchaseId(second.str("id"), rp);

        Resp failed = post(NET + "/orders/" + id + "/receive", fullReceipt(first, w, "2", "2"), w.shop().owner());
        assertEquals(409, failed.status(), failed.raw());
        assertArrayEquals(new long[]{0, 0, 0}, receiptRows(rp), "no purchase, delivery or batch is left");
        assertEquals(0, batchCount(w.biscuits()));
        assertEquals(0, batchCount(w.oil()));
        Resp still = get(NET + "/orders/" + id, w.shop().owner());
        assertEquals("ACCEPTED", still.str("status"), "the order stays receivable");
        assertNull(text(still.at("/lines/0/receivedQuantity")));
        assertEquals(2, still.body().get("history").size(), "no RECEIVED event");

        claimPurchaseId(second.str("id"), null);
        Resp ok = post(NET + "/orders/" + id + "/receive", fullReceipt(first, w, "2", "2"), w.shop().owner());
        assertEquals(200, ok.status(), ok.raw());
        assertArrayEquals(new long[]{1, 1, 2}, receiptRows(rp));
    }

    /** Sets purchase_order.purchase_id directly (the purchase does not exist yet, so foreign keys are paused). */
    private void claimPurchaseId(String orderId, String purchaseId) {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    st.executeUpdate("UPDATE purchase_order SET purchase_id = "
                            + (purchaseId == null ? "NULL" : "UUID_TO_BIN('" + purchaseId + "')")
                            + " WHERE id = UUID_TO_BIN('" + orderId + "')");
                } finally {
                    st.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }

    @Test
    void receiptsAreCheckedAgainstTheOrderAndNothingIsWrittenWhenRefused() {
        World w = world();
        Resp order = placeAndAccept(w, "2", "5");
        String id = order.str("id");
        String rp = receiptPurchaseId(id);
        String boxLine = lineFor(order, w.boxProduct());
        String oilLine = lineFor(order, w.oilProduct());
        String owner = w.shop().owner();

        assertEquals("RECEIPT_LINES", post(NET + "/orders/" + id + "/receive",
                receiptJson(null, null, receiptLine(boxLine, "2", null, null, null)), owner).str("code"), "a line missing");
        assertEquals("RECEIPT_LINES", post(NET + "/orders/" + id + "/receive", receiptJson(null, null,
                receiptLine(boxLine, "2", null, null, null), receiptLine(boxLine, "1", null, null, null)), owner)
                .str("code"), "a line twice");
        assertEquals("RECEIVED_TOO_MUCH", post(NET + "/orders/" + id + "/receive", receiptJson(null, null,
                receiptLine(boxLine, "3", null, null, null), receiptLine(oilLine, "5", null, null, null)), owner)
                .str("code"));
        assertEquals("NOTHING_RECEIVED", post(NET + "/orders/" + id + "/receive", receiptJson(null, null,
                receiptLine(boxLine, "0", null, null, null), receiptLine(oilLine, "0", null, null, null)), owner)
                .str("code"));
        assertEquals("INVALID_NUMBER", post(NET + "/orders/" + id + "/receive", receiptJson(null, null,
                receiptLine(boxLine, "-1", null, null, null), receiptLine(oilLine, "1", null, null, null)), owner)
                .str("code"));
        assertArrayEquals(new long[]{0, 0, 0}, receiptRows(rp));

        // An order that is not accepted cannot be received.
        Resp sent = order(owner, w.conn(), orderLine(w.oilProduct(), "1"));
        assertEquals("ORDER_STATUS", post(NET + "/orders/" + sent.str("id") + "/receive", receiptJson(null, null,
                receiptLine(lineFor(sent, w.oilProduct()), "1", null, null, null)), owner).str("code"));

        // A line received as 0 is left out of the purchase.
        Resp r = post(NET + "/orders/" + id + "/receive", receiptJson(null, null,
                receiptLine(boxLine, "0", null, null, null), receiptLine(oilLine, "5", null, null, null)), owner);
        assertEquals(200, r.status(), r.raw());
        assertArrayEquals(new long[]{1, 1, 1}, receiptRows(rp));
        assertEquals(0, batchCount(w.biscuits()));
        Resp after = get(NET + "/orders/" + id, owner);
        assertEquals("0", text(after.at("/lines/0/receivedQuantity")));
        assertEquals("NOT_RECEIVED", text(after.at("/lines/0/receiptStatus")));
        assertEquals("2", text(after.at("/lines/0/shortageQuantity")));
        assertEquals("FULL", text(after.at("/lines/1/receiptStatus")));
        assertEquals("SHORT", r.str("receiptStatus"));
    }

    @Test
    void goodsCannotBeReceivedUntilTheSupplierIsLinkedToAContact() {
        Shop shop = shop();
        Sup s = approvedSupplier(true);
        String conn = connect(shop.owner(), s);
        String item = item(shop.owner(), "Flour", KG, "30", "35");
        String product = product(s, "Wheat flour", "kg", "28");
        link(shop.owner(), conn, product, item, KG, "1");
        Resp o = order(shop.owner(), conn, orderLine(product, "10"));
        supplierAct(s, o.str("id"), "accept", null);
        String body = receiptJson(null, null, receiptLine(lineFor(o, product), "10", null, null, null));
        assertEquals("CONTACT_NOT_LINKED", post(NET + "/orders/" + o.str("id") + "/receive", body, shop.owner()).str("code"));
        assertEquals(0, batchCount(item));

        linkContact(shop.owner(), conn, supplier(shop.owner(), "Flour mill"));
        assertEquals(200, post(NET + "/orders/" + o.str("id") + "/receive", body, shop.owner()).status());
        assertEquals(1, batchCount(item));
    }

    /** Staff who may order and record purchases; with or without PURCHASE_PRICE_ENTER. */
    private String receiver(String owner, boolean canEnterPrices) {
        String u = unique("rcv");
        String perms = canEnterPrices ? "[\"PURCHASE_ORDER_MANAGE\",\"PURCHASE_RECORD\",\"PURCHASE_PRICE_ENTER\"]"
                : "[\"PURCHASE_ORDER_MANAGE\",\"PURCHASE_RECORD\"]";
        Resp c = createStaff(owner, stepUp(owner, PW), u, perms);
        assertEquals(201, c.status(), c.raw());
        return staffSession(u, c.str("temporaryPassword")).str("accessToken");
    }

    /** Purchase price (line amount, TOTAL basis) per shop item of the purchase a receipt created. */
    private java.util.Map<String, String> purchasePrices(String purchaseId, String owner) {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        for (JsonNode l : get("/api/v1/purchases/" + purchaseId, owner).body().get("lines")) {
            out.put(text(l.get("itemId")), text(l.get("purchasePrice")));
        }
        return out;
    }

    @Test
    void staffWithoutPriceEntryReceiveAtTheSavedOrderPriceAndCannotChangeIt() {
        World w = world();
        String owner = w.shop().owner();
        String staff = receiver(owner, false);

        Resp order = placeAndAccept(w, "2", "3");
        String id = order.str("id");
        String rp = receiptPurchaseId(id);

        // A different price is refused, not ignored, and nothing is written.
        Resp changed = post(NET + "/orders/" + id + "/receive", receiptJson(null, null,
                receiptLine(lineFor(order, w.boxProduct()), "2", "200", null, null),
                receiptLine(lineFor(order, w.oilProduct()), "3", null, null, null)), staff);
        assertEquals(403, changed.status(), changed.raw());
        assertEquals("PERMISSION_DENIED", changed.str("code"));
        assertArrayEquals(new long[]{0, 0, 0}, receiptRows(rp));
        assertEquals(0, batchCount(w.biscuits()));
        assertEquals("ACCEPTED", get(NET + "/orders/" + id, owner).str("status"));

        // Without a price (or repeating the saved one) the saved order prices go into the purchase.
        Resp ok = post(NET + "/orders/" + id + "/receive", receiptJson(null, null,
                receiptLine(lineFor(order, w.boxProduct()), "2", null, null, null),
                receiptLine(lineFor(order, w.oilProduct()), "3", "140.00", null, null)), staff);
        assertEquals(200, ok.status(), ok.raw());
        java.util.Map<String, String> prices = purchasePrices(rp, owner);
        assertMoney("480", prices.get(w.biscuits()), "2 boxes at the saved 240");
        assertMoney("420", prices.get(w.oil()), "3 litres at the saved 140");
        assertArrayEquals(new long[]{1, 1, 2}, receiptRows(rp));
        Resp after = get(NET + "/orders/" + id, owner);
        assertMoney("240", text(after.at("/lines/0/receivedUnitPrice")), "saved price kept on the order");
        assertMoney("140", text(after.at("/lines/1/receivedUnitPrice")), "saved price kept on the order");
    }

    @Test
    void ownersAndStaffWithPriceEntryMayChangeTheSavedPrice() {
        World w = world();
        String owner = w.shop().owner();

        Resp order = placeAndAccept(w, "1", "1");
        Resp byOwner = post(NET + "/orders/" + order.str("id") + "/receive", receiptJson(null, null,
                receiptLine(lineFor(order, w.boxProduct()), "1", "230.50", null, null),
                receiptLine(lineFor(order, w.oilProduct()), "1", null, null, null)), owner);
        assertEquals(200, byOwner.status(), byOwner.raw());
        java.util.Map<String, String> prices = purchasePrices(receiptPurchaseId(order.str("id")), owner);
        assertMoney("230.5", prices.get(w.biscuits()), "changed by the owner");
        assertMoney("140", prices.get(w.oil()), "unchanged line keeps the saved price");

        String pricer = receiver(owner, true);
        Resp order2 = placeAndAccept(w, "2", "1");
        Resp byStaff = post(NET + "/orders/" + order2.str("id") + "/receive", receiptJson(null, null,
                receiptLine(lineFor(order2, w.boxProduct()), "2", "250", null, null),
                receiptLine(lineFor(order2, w.oilProduct()), "1", "135", null, null)), pricer);
        assertEquals(200, byStaff.status(), byStaff.raw());
        prices = purchasePrices(receiptPurchaseId(order2.str("id")), owner);
        assertMoney("500", prices.get(w.biscuits()), "2 x 250");
        assertMoney("135", prices.get(w.oil()), "1 x 135");
    }

    @Test
    void receivingStillNeedsThePurchasePermissionAndRefusalsWriteNothing() {
        World w = world();
        String owner = w.shop().owner();

        // Ordering alone does not allow recording purchases: the existing purchase permission still applies.
        String u = unique("ord");
        Resp c = createStaff(owner, stepUp(owner, PW), u, "[\"PURCHASE_ORDER_MANAGE\"]");
        assertEquals(201, c.status(), c.raw());
        String orderOnly = staffSession(u, c.str("temporaryPassword")).str("accessToken");
        Resp order = placeAndAccept(w, "1", "1");
        String id = order.str("id");
        String rp = receiptPurchaseId(id);
        Resp refused = post(NET + "/orders/" + id + "/receive", fullReceipt(order, w, "1", "1"), orderOnly);
        assertEquals(403, refused.status(), refused.raw());
        assertArrayEquals(new long[]{0, 0, 0}, receiptRows(rp));
        assertEquals(0, batchCount(w.biscuits()));
        assertEquals(0, batchCount(w.oil()));
        assertEquals("ACCEPTED", get(NET + "/orders/" + id, owner).str("status"));

        // Default staff cannot reach purchase orders at all.
        assertEquals(403, post(NET + "/orders/" + id + "/receive", fullReceipt(order, w, "1", "1"),
                w.shop().staff()).status());
        assertArrayEquals(new long[]{0, 0, 0}, receiptRows(rp));
    }

    @Test
    void aFullReceiptIsRecordedAsFull() {
        World w = world();
        Resp order = placeAndAccept(w, "2", "5");
        String id = order.str("id");
        Resp r = post(NET + "/orders/" + id + "/receive", fullReceipt(order, w, "2", "5"), w.shop().owner());
        assertEquals(200, r.status(), r.raw());
        assertEquals("FULL", r.str("receiptStatus"));
        assertEquals("FULL", text(r.at("/order/receiptStatus")));
        for (JsonNode l : r.body().get("order").get("lines")) {
            assertEquals("FULL", text(l.get("receiptStatus")), l.toString());
            assertEquals("0", text(l.get("shortageQuantity")), l.toString());
        }
        JsonNode history = r.body().get("order").get("history");
        assertEquals("FULL", text(history.get(history.size() - 1).get("receiptStatus")));
        assertEquals("FULL", get(PORTAL + "/orders/" + id, w.sup().token()).str("receiptStatus"));
        assertArrayEquals(new long[]{1, 1, 2}, receiptRows(receiptPurchaseId(id)));
    }

    @Test
    void aShortReceiptRecordsTheShortageClosesTheOrderAndAddsOnlyWhatArrived() {
        World w = world();
        Resp order = placeAndAccept(w, "2", "5");
        String id = order.str("id");
        String rp = receiptPurchaseId(id);
        // One box of two arrived; no oil at all.
        Resp r = post(NET + "/orders/" + id + "/receive", receiptJson(null, null,
                receiptLine(lineFor(order, w.boxProduct()), "1", null, "B-1", "2027-01-31"),
                receiptLine(lineFor(order, w.oilProduct()), "0", null, null, null)), w.shop().owner());
        assertEquals(200, r.status(), r.raw());
        assertEquals("SHORT", r.str("receiptStatus"));

        Resp after = get(NET + "/orders/" + id, w.shop().owner());
        assertEquals("RECEIVED", after.str("status"));
        assertEquals("SHORT", after.str("receiptStatus"));
        assertEquals("2", text(after.at("/lines/0/quantity")));
        assertEquals("1", text(after.at("/lines/0/receivedQuantity")));
        assertEquals("1", text(after.at("/lines/0/shortageQuantity")));
        assertEquals("SHORT", text(after.at("/lines/0/receiptStatus")));
        assertEquals("5", text(after.at("/lines/1/quantity")));
        assertEquals("0", text(after.at("/lines/1/receivedQuantity")));
        assertEquals("5", text(after.at("/lines/1/shortageQuantity")));
        assertEquals("NOT_RECEIVED", text(after.at("/lines/1/receiptStatus")));

        // History: one RECEIVED entry, marked SHORT; earlier entries carry no receipt status.
        JsonNode history = after.body().get("history");
        int received = 0;
        for (JsonNode e : history) {
            if ("RECEIVED".equals(text(e.get("toStatus")))) {
                received++;
                assertEquals("SHORT", text(e.get("receiptStatus")));
            } else {
                assertNull(text(e.get("receiptStatus")), e.toString());
            }
        }
        assertEquals(1, received);
        Resp supplierView = get(PORTAL + "/orders/" + id, w.sup().token());
        assertEquals("SHORT", supplierView.str("receiptStatus"));
        assertEquals("NOT_RECEIVED", text(supplierView.at("/lines/1/receiptStatus")));

        // Only what arrived reaches stock: 1 box = 24 pcs, one batch; nothing for the oil.
        assertArrayEquals(new long[]{1, 1, 1}, receiptRows(rp));
        assertEquals(0, batchCount(w.oil()));
        Resp batches = get("/api/v1/batches?itemId=" + w.biscuits(), w.shop().owner());
        assertEquals(1, batches.body().size(), batches.raw());
        assertEquals("24", text(batches.body().get(0).get("quantity")));
        assertEquals("2027-01-31", text(batches.body().get(0).get("expiryDate")));
        assertEquals(1, get("/api/v1/purchases/" + rp, w.shop().owner()).body().get("lines").size());

        // The order is closed for good: the missing goods cannot be received against it, nor can it be cancelled.
        Resp again = post(NET + "/orders/" + id + "/receive", receiptJson(null, null,
                receiptLine(lineFor(order, w.boxProduct()), "1", null, null, null),
                receiptLine(lineFor(order, w.oilProduct()), "5", null, null, null)), w.shop().owner());
        assertEquals(409, again.status(), again.raw());
        assertEquals("ORDER_ALREADY_RECEIVED", again.str("code"));
        assertEquals(409, post(NET + "/orders/" + id + "/cancel", null, w.shop().owner()).status());
        assertArrayEquals(new long[]{1, 1, 1}, receiptRows(rp));
        assertEquals("SHORT", jdbc.queryForObject("SELECT receipt_status FROM purchase_order WHERE id = UUID_TO_BIN(?)",
                String.class, id));
    }

    // ================================================================== the receipt's purchase is locked

    private static String syncPurchaseOp(String id, String action, Long base, String purchase, String reason) {
        return """
                {"opId":"%s","entityType":"PURCHASE","entityId":"%s","action":"%s","baseVersion":%s,"purchase":%s,
                 "reason":%s}
                """.formatted(newId(), id, action, base, purchase == null ? "null" : purchase, q(reason));
    }

    private static String syncDeliveryOp(String deliveryId, String delivery) {
        return """
                {"opId":"%s","entityType":"DELIVERY","entityId":"%s","action":"UPSERT","baseVersion":null,"delivery":%s}
                """.formatted(newId(), deliveryId, delivery);
    }

    private void assertLockedBySync(Resp r) {
        assertEquals(200, r.status(), r.raw());
        JsonNode result = r.body().get("results").get(0);
        assertEquals("REJECTED", text(result.get("status")), r.raw());
        assertEquals("PURCHASE_FROM_ORDER", text(result.get("code")), r.raw());
    }

    @Test
    void thePurchaseOfAReceiptCannotBeChangedWhileOrdinaryPurchasesStillCan() {
        World w = world();
        String owner = w.shop().owner();
        Resp order = placeAndAccept(w, "2", "5");
        String id = order.str("id");
        String rp = receiptPurchaseId(id);
        Resp received = post(NET + "/orders/" + id + "/receive", fullReceipt(order, w, "2", "4"), owner);
        assertEquals(200, received.status(), received.raw());

        Resp purchase = get("/api/v1/purchases/" + rp, owner);
        long version = version(purchase);
        List<String> lines = new ArrayList<>();
        String oilLine = null;
        for (JsonNode l : purchase.body().get("lines")) {
            lines.add(pline(text(l.get("id")), text(l.get("itemId")), "99", text(l.get("unitId")), "TOTAL", "1", null));
            if (w.oil().equals(text(l.get("itemId")))) oilLine = text(l.get("id"));
        }
        String changedBody = purchaseJson(w.contact(), "INV-77", "2026-11-16", true, null, lines.toArray(String[]::new));
        String deliveryId = newId();
        String extraDelivery = delivery(deliveryId, rp, "2026-11-17", receipt(newId(), oilLine, "1", null, null));
        String correction = delivery(newId(), null, "2026-11-17", correction(newId(), oilLine, "-1",
                text(purchase.at("/deliveries/0/lines/0/id")), "Counted wrong"));

        // Through the purchase API: edit, more goods, a correction, cancel and delete are all refused.
        Resp edit = putIfMatch("/api/v1/purchases/" + rp, changedBody, owner, version);
        assertEquals(409, edit.status(), edit.raw());
        assertEquals("PURCHASE_FROM_ORDER", edit.str("code"));
        Resp more = post("/api/v1/purchases/" + rp + "/deliveries", extraDelivery, owner);
        assertEquals(409, more.status(), more.raw());
        assertEquals("PURCHASE_FROM_ORDER", more.str("code"));
        assertEquals("PURCHASE_FROM_ORDER", post("/api/v1/purchases/" + rp + "/deliveries", correction, owner)
                .str("code"));
        Resp cancel = callWithHeader("POST", "/api/v1/purchases/" + rp + "/cancel", "{\"reason\":\"Returned\"}", owner,
                "If-Match", Long.toString(version));
        assertEquals(409, cancel.status(), cancel.raw());
        assertEquals("PURCHASE_FROM_ORDER", cancel.str("code"));
        Resp delete = callWithHeader("DELETE", "/api/v1/purchases/" + rp, null, owner, "If-Match",
                Long.toString(version));
        assertEquals(409, delete.status(), delete.raw());
        assertEquals("PURCHASE_FROM_ORDER", delete.str("code"));

        // Through offline sync: the same rules.
        assertLockedBySync(post("/api/v1/sync/push", "{\"ops\":[" + syncPurchaseOp(rp, "UPSERT", version, changedBody,
                null) + "]}", owner));
        assertLockedBySync(post("/api/v1/sync/push", "{\"ops\":[" + syncPurchaseOp(rp, "DELETE", version, null,
                "Returned") + "]}", owner));
        assertLockedBySync(post("/api/v1/sync/push", "{\"ops\":[" + syncDeliveryOp(deliveryId, extraDelivery) + "]}",
                owner));

        // Nothing changed: the purchase, its one delivery and its batches are as the receipt left them.
        Resp after = get("/api/v1/purchases/" + rp, owner);
        assertEquals(version, version(after));
        assertEquals("RECEIVED", after.str("status"), after.raw());
        assertEquals(1, after.body().get("deliveries").size());
        assertArrayEquals(new long[]{1, 1, 2}, receiptRows(rp));
        Resp oilBatches = get("/api/v1/batches?itemId=" + w.oil(), owner);
        assertEquals("4", text(oilBatches.body().get(0).get("quantity")), "still only what arrived");
        assertEquals("RECEIVED", get(NET + "/orders/" + id, owner).str("status"));

        // An ordinary purchase of the same shop, supplier and items: edit, delivery and cancel still work.
        String line = newId();
        Resp ordinary = post("/api/v1/purchases", purchaseJson(w.contact(), "ORD-1", "2026-11-16", true,
                delivery(newId(), null, "2026-11-16", receipt(newId(), line, "2", null, null)),
                pline(line, w.oil(), "5", LITRE, "PER_UNIT", "140", null)), owner);
        assertEquals(201, ordinary.status(), ordinary.raw());
        String op = ordinary.str("id");
        Resp delivered = post("/api/v1/purchases/" + op + "/deliveries",
                delivery(newId(), op, "2026-11-17", receipt(newId(), line, "3", null, null)), owner);
        assertEquals(201, delivered.status(), delivered.raw());
        Resp edited = putIfMatch("/api/v1/purchases/" + op, purchaseJson(w.contact(), "ORD-1B", "2026-11-16", true, null,
                pline(line, w.oil(), "6", LITRE, "PER_UNIT", "140", null)), owner, version(delivered));
        assertEquals(200, edited.status(), edited.raw());
        Resp cancelled = callWithHeader("POST", "/api/v1/purchases/" + op + "/cancel", "{\"reason\":\"Returned\"}",
                owner, "If-Match", Long.toString(version(edited)));
        assertEquals(200, cancelled.status(), cancelled.raw());
    }
}
