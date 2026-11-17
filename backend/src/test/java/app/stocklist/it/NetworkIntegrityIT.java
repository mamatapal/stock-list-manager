package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Order idempotency keys, and the database's own tenant rules: rows of the supplier network that name a shop, a supplier
 * and a parent record must name one consistent shop-supplier connection, whatever code (or direct SQL) writes them.
 */
class NetworkIntegrityIT extends NetworkTestSupport {

    record World(Shop shop, Sup sup, String conn, String product, String product2, String item) {}

    private World world() {
        Shop shop = shop();
        Sup s = approvedSupplier(true);
        String conn = connect(shop.owner(), s);
        String item = item(shop.owner(), "Tea", KG, "300", "340");
        String item2 = item(shop.owner(), "Salt", KG, "20", "24");
        String p1 = product(s, "Tea", "kg", "280");
        String p2 = product(s, "Salt", "kg", "18");
        assertEquals(200, link(shop.owner(), conn, p1, item, KG, "1").status());
        assertEquals(200, link(shop.owner(), conn, p2, item2, KG, "1").status());
        return new World(shop, s, conn, p1, p2, item);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long ordersWithId(String id) {
        return count("SELECT COUNT(*) FROM purchase_order WHERE id = UUID_TO_BIN(?)", id);
    }

    private long orderEvents(String id) {
        return count("SELECT COUNT(*) FROM purchase_order_event WHERE order_id = UUID_TO_BIN(?)", id);
    }

    // ================================================================== idempotency

    @Test
    void theSameRequestWithTheSameKeyReturnsTheOriginalOrder() {
        World w = world();
        String id = newId();
        String body = orderJson(id, w.conn(), orderLine(w.product(), "2"), orderLine(w.product2(), "1.5"));
        Resp first = post(NET + "/orders", body, w.shop().owner());
        assertEquals(201, first.status(), first.raw());
        assertEquals(id, first.str("id"));

        Resp again = post(NET + "/orders", body, w.shop().owner());
        assertTrue(again.status() == 200 || again.status() == 201, again.raw());
        assertEquals(id, again.str("id"));
        assertEquals(first.str("totalAmount"), again.str("totalAmount"));
        // The same quantities written differently are the same request.
        Resp same = post(NET + "/orders", orderJson(id, w.conn(), orderLine(w.product(), "2.000"),
                orderLine(w.product2(), "1.50")), w.shop().owner());
        assertEquals(id, same.str("id"), same.raw());
        assertEquals(1, ordersWithId(id));
        assertEquals(1, orderEvents(id));
        assertEquals(2, count("SELECT COUNT(*) FROM purchase_order_line WHERE order_id = UUID_TO_BIN(?)", id));
    }

    @Test
    void reusingAKeyForADifferentOrderIsRefused() {
        World w = world();
        String owner = w.shop().owner();
        String id = newId();
        assertEquals(201, post(NET + "/orders", orderJson(id, w.conn(), orderLine(w.product(), "2")), owner).status());

        Resp quantity = post(NET + "/orders", orderJson(id, w.conn(), orderLine(w.product(), "3")), owner);
        assertEquals(409, quantity.status(), quantity.raw());
        assertEquals("ORDER_ID_REUSED", quantity.str("code"));

        Resp product = post(NET + "/orders", orderJson(id, w.conn(), orderLine(w.product2(), "2")), owner);
        assertEquals("ORDER_ID_REUSED", product.str("code"), product.raw());

        Resp extraLine = post(NET + "/orders", orderJson(id, w.conn(), orderLine(w.product(), "2"),
                orderLine(w.product2(), "1")), owner);
        assertEquals("ORDER_ID_REUSED", extraLine.str("code"), extraLine.raw());

        Resp note = post(NET + "/orders", """
                {"id":"%s","connectionId":"%s","note":"Another note","lines":[%s]}
                """.formatted(id, w.conn(), orderLine(w.product(), "2")), owner);
        assertEquals("ORDER_ID_REUSED", note.str("code"), note.raw());

        // A different connection of the same shop.
        Sup s2 = approvedSupplier(true);
        String conn2 = connect(owner, s2);
        String p3 = product(s2, "Tea", "kg", "275");
        assertEquals(200, link(owner, conn2, p3, w.item(), KG, "1").status());
        Resp connection = post(NET + "/orders", orderJson(id, conn2, orderLine(p3, "2")), owner);
        assertEquals(409, connection.status(), connection.raw());
        assertEquals("ORDER_ID_REUSED", connection.str("code"));

        // Another shop using the same key learns nothing about the order.
        World other = world();
        Resp foreign = post(NET + "/orders", orderJson(id, other.conn(), orderLine(other.product(), "2")),
                other.shop().owner());
        assertEquals(409, foreign.status(), foreign.raw());
        assertEquals("ORDER_ID_REUSED", foreign.str("code"));

        // The original order is untouched and was written once.
        Resp stored = get(NET + "/orders/" + id, owner);
        assertEquals(w.conn(), stored.str("connectionId"));
        assertEquals(1, stored.body().get("lines").size());
        assertEquals("2", text(stored.at("/lines/0/quantity")));
        assertEquals(1, ordersWithId(id));
        assertEquals(1, orderEvents(id));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_order WHERE connection_id = UUID_TO_BIN(?)", conn2));
    }

    @Test
    void concurrentRequestsWithTheSameKeyPlaceOneOrder() throws Exception {
        World w = world();
        String id = newId();
        String body = orderJson(id, w.conn(), orderLine(w.product(), "4"));
        List<Resp> results = concurrently(6, () -> post(NET + "/orders", body, w.shop().owner()));
        for (Resp r : results) {
            assertTrue(r.status() == 200 || r.status() == 201, r.raw());
            assertEquals(id, r.str("id"));
        }
        assertEquals(1, ordersWithId(id));
        assertEquals(1, orderEvents(id));
        assertEquals(1, count("SELECT COUNT(*) FROM purchase_order_line WHERE order_id = UUID_TO_BIN(?)", id));
    }

    @Test
    void concurrentRequestsWithTheSameKeyButDifferentConnectionsPlaceOneOrder() throws Exception {
        World w = world();
        String owner = w.shop().owner();
        Sup s2 = approvedSupplier(true);
        String conn2 = connect(owner, s2);
        String p3 = product(s2, "Tea", "kg", "275");
        assertEquals(200, link(owner, conn2, p3, w.item(), KG, "1").status());
        String id = newId();
        String a = orderJson(id, w.conn(), orderLine(w.product(), "1"));
        String b = orderJson(id, conn2, orderLine(p3, "1"));
        int[] n = {0};
        List<Resp> results = concurrently(6, () -> {
            int i;
            synchronized (n) {
                i = n[0]++;
            }
            return post(NET + "/orders", i % 2 == 0 ? a : b, owner);
        });
        int placed = 0;
        for (Resp r : results) {
            if (r.status() == 201 || r.status() == 200) placed++;
            else assertEquals(409, r.status(), r.raw());
        }
        assertTrue(placed >= 1, "at least the first request is placed");
        assertEquals(1, ordersWithId(id));
        assertEquals(1, orderEvents(id));
        String connection = get(NET + "/orders/" + id, owner).str("connectionId");
        for (Resp r : results) {
            if (r.status() == 201 || r.status() == 200) assertEquals(connection, r.str("connectionId"), r.raw());
        }
    }

    private List<Resp> concurrently(int n, java.util.concurrent.Callable<Resp> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Resp>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return call.call();
            }));
        }
        start.countDown();
        List<Resp> out = new ArrayList<>();
        for (Future<Resp> f : futures) out.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        return out;
    }

    // ================================================================== database tenant rules

    private String businessOf(String connectionId) {
        return jdbc.queryForObject("SELECT BIN_TO_UUID(business_id) FROM supplier_connection WHERE id = UUID_TO_BIN(?)",
                String.class, connectionId);
    }

    private void insertOrder(String id, String businessId, String orgId, String connectionId) {
        jdbc.update("""
                INSERT INTO purchase_order (id, business_id, supplier_org_id, connection_id, status, total_amount,
                    line_count, created_by, created_at, updated_at, receipt_purchase_id, version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), 'SENT', 0, 1, UUID_TO_BIN(?),
                    NOW(6), NOW(6), UUID_TO_BIN(?), 0)""", id, businessId, orgId, connectionId, newId(), newId());
    }

    private void insertConnectionEvent(String connectionId, String businessId, String orgId) {
        jdbc.update("""
                INSERT INTO supplier_connection_event (connection_id, business_id, supplier_org_id, actor_type,
                    to_status, created_at)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), 'SYSTEM', 'ACTIVE', NOW(6))""",
                connectionId, businessId, orgId);
    }

    private void insertOrderEvent(String orderId, String businessId, String orgId) {
        jdbc.update("""
                INSERT INTO purchase_order_event (order_id, business_id, supplier_org_id, actor_type, to_status,
                    created_at)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), 'SYSTEM', 'SENT', NOW(6))""",
                orderId, businessId, orgId);
    }

    private void insertLine(String orderId, String businessId, String orgId, String productId, String itemId,
                            int lineNo) {
        jdbc.update("""
                INSERT INTO purchase_order_line (id, business_id, supplier_org_id, order_id, line_no,
                    supplier_product_id, product_name_en, unit, quantity, unit_price, line_amount, item_id,
                    item_unit_id, units_per_pack)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?), 'Test',
                    'kg', 1, 1, 1, UUID_TO_BIN(?), UUID_TO_BIN(?), 1)""",
                newId(), businessId, orgId, orderId, lineNo, productId, itemId, KG);
    }

    private void insertLink(String businessId, String orgId, String productId, String itemId) {
        jdbc.update("""
                INSERT INTO supplier_product_link (id, business_id, supplier_org_id, supplier_product_id, item_id,
                    unit_id, units_per_pack, created_by, updated_by, version, created_at, updated_at)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?),
                    UUID_TO_BIN(?), 1, UUID_TO_BIN(?), UUID_TO_BIN(?), 0, NOW(6), NOW(6))""",
                newId(), businessId, orgId, productId, itemId, KG, newId(), newId());
    }

    private static void refused(Runnable insert) {
        DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class, insert::run);
        assertTrue(String.valueOf(e.getMostSpecificCause().getMessage()).contains("foreign key constraint fails"),
                e.getMostSpecificCause().getMessage());
    }

    @Test
    void theDatabaseRefusesRowsThatMixShopsSuppliersAndConnections() {
        // Shop A with supplier 1, shop B with supplier 2.
        World a = world();
        World b = world();
        String shopA = businessOf(a.conn());
        String shopB = businessOf(b.conn());
        String sup1 = a.sup().orgId();
        String sup2 = b.sup().orgId();
        String itemB = b.item();

        // Control: rows that name one consistent connection are accepted.
        String order = newId();
        insertOrder(order, shopA, sup1, a.conn());
        insertConnectionEvent(a.conn(), shopA, sup1);
        insertOrderEvent(order, shopA, sup1);
        insertLine(order, shopA, sup1, a.product(), a.item(), 1);

        // Purchase order: shop, supplier and connection must be one connection.
        refused(() -> insertOrder(newId(), shopA, sup2, a.conn()));   // another supplier
        refused(() -> insertOrder(newId(), shopB, sup1, a.conn()));   // another shop
        refused(() -> insertOrder(newId(), shopA, sup1, b.conn()));   // another shop's connection
        refused(() -> insertOrder(newId(), shopA, sup2, b.conn()));   // supplier and connection of shop B

        // Connection events must match their connection.
        refused(() -> insertConnectionEvent(a.conn(), shopA, sup2));
        refused(() -> insertConnectionEvent(a.conn(), shopB, sup1));

        // Order events must match their order.
        refused(() -> insertOrderEvent(order, shopA, sup2));
        refused(() -> insertOrderEvent(order, shopB, sup1));

        // Order lines: the order's shop and supplier, and a product of that supplier.
        refused(() -> insertLine(order, shopA, sup1, b.product(), a.item(), 2));   // product of supplier 2
        refused(() -> insertLine(order, shopA, sup2, b.product(), a.item(), 3));   // claims supplier 2
        refused(() -> insertLine(order, shopB, sup1, a.product(), itemB, 4));      // claims shop B

        // Product links: only for a supplier the shop has a connection with, and only that supplier's products.
        refused(() -> insertLink(shopA, sup2, b.product(), a.item()));             // no connection A-2
        refused(() -> insertLink(shopA, sup1, b.product(), a.item()));             // product of supplier 2
        refused(() -> insertLink(shopB, sup1, a.product(), itemB));                // no connection B-1

        assertEquals(1, count("SELECT COUNT(*) FROM purchase_order WHERE business_id = UUID_TO_BIN(?) "
                + "AND connection_id = UUID_TO_BIN(?) AND id = UUID_TO_BIN(?)", shopA, a.conn(), order));
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_order WHERE business_id = UUID_TO_BIN(?)", shopB));
    }

    // ================================================================== receipt status CHECK constraints

    private void insertOrderWithStatus(String id, World w, String status, String receiptStatus) {
        jdbc.update("""
                INSERT INTO purchase_order (id, business_id, supplier_org_id, connection_id, status, total_amount,
                    line_count, created_by, created_at, updated_at, receipt_purchase_id, version, receipt_status)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, 0, 1, UUID_TO_BIN(?),
                    NOW(6), NOW(6), UUID_TO_BIN(?), 0, ?)""",
                id, businessOf(w.conn()), w.sup().orgId(), w.conn(), status, newId(), newId(), receiptStatus);
    }

    private void insertOrderEventWithStatus(String orderId, World w, String toStatus, String receiptStatus) {
        jdbc.update("""
                INSERT INTO purchase_order_event (order_id, business_id, supplier_org_id, actor_type, to_status,
                    receipt_status, created_at)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), 'SHOP', ?, ?, NOW(6))""",
                orderId, businessOf(w.conn()), w.sup().orgId(), toStatus, receiptStatus);
    }

    private static void violates(String constraint, Runnable insert) {
        org.springframework.dao.DataAccessException e =
                assertThrows(org.springframework.dao.DataAccessException.class, insert::run);
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        assertTrue(message.contains(constraint), message);
    }

    private String receiptStatusOf(String orderId) {
        return jdbc.queryForObject("SELECT receipt_status FROM purchase_order WHERE id = UUID_TO_BIN(?)", String.class,
                orderId);
    }

    @Test
    void anOrderHasAReceiptStatusExactlyWhenItIsReceived() {
        World w = world();
        String check = "chk_purchase_order_receipt";

        // RECEIVED needs FULL or SHORT: NULL (an UNKNOWN IN test) and other values are refused.
        violates(check, () -> insertOrderWithStatus(newId(), w, "RECEIVED", null));
        violates(check, () -> insertOrderWithStatus(newId(), w, "RECEIVED", "PARTIAL"));
        String full = newId();
        insertOrderWithStatus(full, w, "RECEIVED", "FULL");
        assertEquals("FULL", receiptStatusOf(full));
        String shortOrder = newId();
        insertOrderWithStatus(shortOrder, w, "RECEIVED", "SHORT");
        assertEquals("SHORT", receiptStatusOf(shortOrder));

        // Every other status must have none.
        for (String status : List.of("SENT", "ACCEPTED", "REJECTED", "DISPATCHED", "CANCELLED")) {
            violates(check, () -> insertOrderWithStatus(newId(), w, status, "FULL"));
            violates(check, () -> insertOrderWithStatus(newId(), w, status, "SHORT"));
            String ok = newId();
            insertOrderWithStatus(ok, w, status, null);
            assertEquals(null, receiptStatusOf(ok), status);
        }

        // Nor can an existing row be changed into an inconsistent state.
        violates(check, () -> jdbc.update("UPDATE purchase_order SET receipt_status = NULL WHERE id = UUID_TO_BIN(?)",
                full));
        violates(check, () -> jdbc.update("UPDATE purchase_order SET status = 'DISPATCHED' WHERE id = UUID_TO_BIN(?)",
                shortOrder));
        assertEquals("FULL", receiptStatusOf(full));
        assertEquals("SHORT", receiptStatusOf(shortOrder));
    }

    @Test
    void anOrderHistoryEntryHasAReceiptStatusExactlyWhenItRecordsTheReceipt() {
        World w = world();
        String check = "chk_po_event_receipt";
        String order = newId();
        insertOrderWithStatus(order, w, "SENT", null);
        String events = "SELECT COUNT(*) FROM purchase_order_event WHERE order_id = UUID_TO_BIN(?)";

        violates(check, () -> insertOrderEventWithStatus(order, w, "RECEIVED", null));
        violates(check, () -> insertOrderEventWithStatus(order, w, "RECEIVED", "PARTIAL"));
        insertOrderEventWithStatus(order, w, "RECEIVED", "FULL");
        insertOrderEventWithStatus(order, w, "RECEIVED", "SHORT");
        assertEquals(2, count(events, order));

        for (String status : List.of("SENT", "ACCEPTED", "REJECTED", "DISPATCHED", "CANCELLED")) {
            violates(check, () -> insertOrderEventWithStatus(order, w, status, "FULL"));
            violates(check, () -> insertOrderEventWithStatus(order, w, status, "SHORT"));
            insertOrderEventWithStatus(order, w, status, null);
        }
        assertEquals(7, count(events, order));
        assertEquals(2, count(events + " AND receipt_status IS NOT NULL AND to_status = 'RECEIVED'", order));
    }
}
