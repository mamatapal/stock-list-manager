package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Ending a connection racing every order change. Each change locks the connection first and then the order (ending
 * locks the connection and then its open orders), so the two run one after the other in either order, and both
 * orders give a valid result: no order is placed or left SENT/ACCEPTED on an ended connection, no dispatched order is
 * cancelled, a receipt either happens completely or not at all, and no call fails with a deadlock or a server error.
 * Each race is repeated over several rounds so both orders of arrival are exercised.
 */
class NetworkConcurrencyIT extends NetworkTestSupport {
    private static final int ROUNDS = 8;

    record World(Shop shop, Sup sup, String conn, String product, String item) {}

    private ExecutorService pool;

    @BeforeEach
    void startPool() {
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    private World world() {
        Shop shop = shop();
        Sup s = approvedSupplier(true);
        String conn = connect(shop.owner(), s);
        linkContact(shop.owner(), conn, supplier(shop.owner(), "Network contact"));
        String item = item(shop.owner(), "Pulses", KG, "90", "100");
        String product = product(s, "Toor dal", "kg", "85");
        assertEquals(200, link(shop.owner(), conn, product, item, KG, "1").status());
        return new World(shop, s, conn, product, item);
    }

    /** Re-opens the connection after a round ended it (a new request by the shop, accepted by the supplier). */
    private void reopen(World w) {
        assertEquals(w.conn(), connect(w.shop().owner(), w.sup()));
    }

    /** Ends the connection, from the shop or the supplier side. */
    private Resp end(World w, int round) {
        return round % 2 == 0
                ? post(NET + "/connections/" + w.conn() + "/end", "{\"reason\":\"race\"}", w.shop().owner())
                : post(PORTAL + "/connections/" + w.conn() + "/end", "{\"reason\":\"race\"}", w.sup().token());
    }

    /** Runs both calls at the same moment and returns both answers. */
    private Resp[] race(Callable<Resp> a, Callable<Resp> b) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<Resp> fa = pool.submit(() -> {
            barrier.await();
            return a.call();
        });
        Future<Resp> fb = pool.submit(() -> {
            barrier.await();
            return b.call();
        });
        return new Resp[]{fa.get(60, TimeUnit.SECONDS), fb.get(60, TimeUnit.SECONDS)};
    }

    private Resp placeSent(World w) {
        return order(w.shop().owner(), w.conn(), orderLine(w.product(), "3"));
    }

    private String status(World w, String orderId) {
        return get(NET + "/orders/" + orderId, w.shop().owner()).str("status");
    }

    private long openOrders(World w) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE connection_id = UUID_TO_BIN(?) "
                + "AND status IN ('SENT', 'ACCEPTED')", Long.class, w.conn());
    }

    private long dispatchedThenCancelled(World w) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_event e JOIN purchase_order o ON o.id = e.order_id"
                + " WHERE o.connection_id = UUID_TO_BIN(?) AND e.from_status = 'DISPATCHED' AND e.to_status = 'CANCELLED'",
                Long.class, w.conn());
    }

    private static void ended(Resp end) {
        assertEquals(200, end.status(), end.raw());
        assertEquals("ENDED", end.str("status"), end.raw());
    }

    private static void refusedNotFailed(Resp r) {
        assertEquals(409, r.status(), "a refused change answers 409, never a server error: " + r.raw());
    }

    @Test
    void endingAConnectionWhileAnOrderIsPlaced() throws Exception {
        World w = world();
        for (int round = 0; round < ROUNDS; round++) {
            if (round > 0) reopen(w);
            int r = round;
            Resp[] out = race(() -> end(w, r),
                    () -> post(NET + "/orders", orderJson(null, w.conn(), orderLine(w.product(), "1")),
                            w.shop().owner()));
            ended(out[0]);
            Resp placed = out[1];
            if (placed.status() == 201) {
                // Placed first: the end that followed cancelled it.
                assertEquals("CANCELLED", status(w, placed.str("id")), "round " + round);
            } else {
                refusedNotFailed(placed);
                assertEquals("CONNECTION_NOT_ACTIVE", placed.str("code"), placed.raw());
            }
            assertEquals(0, openOrders(w), "no open order on an ended connection, round " + round);
        }
    }

    @Test
    void endingAConnectionWhileTheSupplierAcceptsAnOrder() throws Exception {
        World w = world();
        for (int round = 0; round < ROUNDS; round++) {
            if (round > 0) reopen(w);
            String id = placeSent(w).str("id");
            int r = round;
            Resp[] out = race(() -> end(w, r), () -> supplierAct(w.sup(), id, "accept", null));
            ended(out[0]);
            if (out[1].status() != 200) refusedNotFailed(out[1]);
            // Accepted first or not at all: either way the end cancels it, and it never stays ACCEPTED.
            assertEquals("CANCELLED", status(w, id), "round " + round);
            assertEquals(0, openOrders(w), "round " + round);
        }
    }

    @Test
    void endingAConnectionWhileTheSupplierDispatchesAnOrder() throws Exception {
        World w = world();
        for (int round = 0; round < ROUNDS; round++) {
            if (round > 0) reopen(w);
            String id = placeSent(w).str("id");
            assertEquals(200, supplierAct(w.sup(), id, "accept", null).status());
            int r = round;
            Resp[] out = race(() -> end(w, r), () -> supplierAct(w.sup(), id, "dispatch", null));
            ended(out[0]);
            if (out[1].status() == 200) {
                assertEquals("DISPATCHED", status(w, id), "a dispatched order is never cancelled, round " + round);
            } else {
                refusedNotFailed(out[1]);
                assertEquals("CANCELLED", status(w, id), "round " + round);
            }
            assertEquals(0, openOrders(w), "round " + round);
            assertEquals(0, dispatchedThenCancelled(w));
        }
    }

    @Test
    void theShopCancellingWhileTheSupplierDispatches() throws Exception {
        World w = world();
        for (int round = 0; round < ROUNDS; round++) {
            String id = placeSent(w).str("id");
            assertEquals(200, supplierAct(w.sup(), id, "accept", null).status());
            Resp[] out = race(() -> post(NET + "/orders/" + id + "/cancel", null, w.shop().owner()),
                    () -> supplierAct(w.sup(), id, "dispatch", null));
            assertEquals(1, (out[0].status() == 200 ? 1 : 0) + (out[1].status() == 200 ? 1 : 0),
                    "exactly one wins: " + out[0].raw() + " / " + out[1].raw());
            if (out[0].status() == 200) {
                refusedNotFailed(out[1]);
                assertEquals("CANCELLED", status(w, id));
            } else {
                refusedNotFailed(out[0]);
                assertEquals("ORDER_STATUS", out[0].str("code"));
                assertEquals("DISPATCHED", status(w, id));
            }
        }
        assertEquals(0, dispatchedThenCancelled(w));
    }

    @Test
    void endingAConnectionWhileGoodsAreReceived() throws Exception {
        World w = world();
        for (int round = 0; round < ROUNDS; round++) {
            if (round > 0) reopen(w);
            Resp order = placeSent(w);
            String id = order.str("id");
            assertEquals(200, supplierAct(w.sup(), id, "accept", null).status());
            // Odd rounds: the goods are already on their way (DISPATCHED), so ending the connection leaves the order
            // receivable and both calls succeed. Even rounds: ACCEPTED, so whichever comes first wins.
            boolean dispatched = round % 2 == 1;
            if (dispatched) assertEquals(200, supplierAct(w.sup(), id, "dispatch", null).status());
            String rp = jdbc.queryForObject("SELECT BIN_TO_UUID(receipt_purchase_id) FROM purchase_order "
                    + "WHERE id = UUID_TO_BIN(?)", String.class, id);
            String body = receiptJson(null, null, receiptLine(lineFor(order, w.product()), "3", null, null, null));
            int r = round;
            Resp[] out = race(() -> end(w, r),
                    () -> post(NET + "/orders/" + id + "/receive", body, w.shop().owner()));
            ended(out[0]);
            Resp received = out[1];
            if (dispatched) assertEquals(200, received.status(), "dispatched goods stay receivable: " + received.raw());
            if (received.status() == 200) {
                assertEquals("RECEIVED", status(w, id), "round " + round);
                assertArrayEquals(new long[]{1, 1, 1}, receiptRows(rp), "round " + round);
            } else {
                refusedNotFailed(received);
                assertEquals("ORDER_STATUS", received.str("code"), received.raw());
                assertEquals("CANCELLED", status(w, id), "round " + round);
                assertArrayEquals(new long[]{0, 0, 0}, receiptRows(rp), "nothing received, round " + round);
            }
            assertEquals(0, openOrders(w), "round " + round);
        }
        long batches = batchCount(w.item());
        long received = jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE connection_id = UUID_TO_BIN(?) "
                + "AND status = 'RECEIVED'", Long.class, w.conn());
        assertEquals(received, batches, "one batch per received order, none for cancelled ones");
        assertTrue(received >= ROUNDS / 2, "every dispatched order was received");
    }
}
