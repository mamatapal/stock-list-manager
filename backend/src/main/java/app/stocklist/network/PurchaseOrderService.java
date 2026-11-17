package app.stocklist.network;

import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.Decimals;
import app.stocklist.item.ItemService;
import app.stocklist.network.NetworkDtos.OrderInput;
import app.stocklist.network.NetworkDtos.OrderLineDto;
import app.stocklist.network.NetworkDtos.OrderLineInput;
import app.stocklist.network.NetworkDtos.ReasonInput;
import app.stocklist.network.NetworkDtos.ReceiptDto;
import app.stocklist.network.NetworkDtos.ReceiptInput;
import app.stocklist.network.NetworkDtos.ReceiptLineInput;
import app.stocklist.network.NetworkDtos.ShopOrderDto;
import app.stocklist.network.NetworkDtos.SupplierOrderDto;
import app.stocklist.permission.Permission;
import app.stocklist.purchase.DeliveryLine;
import app.stocklist.purchase.PurchaseCalculator;
import app.stocklist.purchase.PurchaseDtos.DeliveryInput;
import app.stocklist.purchase.PurchaseDtos.DeliveryLineInput;
import app.stocklist.purchase.PurchaseDtos.LineInput;
import app.stocklist.purchase.PurchaseDtos.PurchaseInput;
import app.stocklist.purchase.PurchaseService;
import app.stocklist.supplieraccount.SupplierAccess;
import app.stocklist.supplieraccount.SupplierPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase orders between a shop and a connected supplier, and the goods receipt.
 *
 * <p>Placing, accepting, rejecting, dispatching or cancelling an order never touches stock. Only the goods receipt
 * changes stock, and it does so through the existing purchase workflow: in one transaction it creates a completed
 * purchase with one delivery through {@link PurchaseService#create} (with all its checks, its permission rules and
 * its batch creation), links that purchase to the order and marks the order RECEIVED. The purchase id is fixed when
 * the order is placed, the order row is locked while receiving, and purchase_order.purchase_id is unique, so a
 * repeated or concurrent receipt can never create a second purchase, delivery or batch. Any failure rolls the whole
 * receipt back and the order stays receivable.
 *
 * <p>Shop callers need PURCHASE_ORDER_MANAGE (checked first; receiving also needs the existing PURCHASE_RECORD, and
 * changing a saved price PURCHASE_PRICE_ENTER). Supplier callers must belong to the order's APPROVED organisation.
 *
 * <p>Concurrency: every change locks the order's connection row first and then the order row (ending a connection
 * locks the connection and then its open orders, see ConnectionService). One lock order everywhere means no deadlocks,
 * and changes to one connection and its orders happen one after another: an order cannot be placed on, or accepted or
 * dispatched under, a connection that has ended, and ending a connection never cancels a dispatched order.
 */
@Service
public class PurchaseOrderService {
    static final int MAX_LINES = 200;

    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository lines;
    private final PurchaseOrderEventRepository events;
    private final SupplierConnectionRepository connections;
    private final SupplierProductRepository products;
    private final SupplierProductLinkRepository links;
    private final SupplierAccess supplierAccess;
    private final ItemService items;
    private final PurchaseService purchases;
    private final NetworkSupport support;
    private final Clock clock;
    private final ZoneId zone;

    public PurchaseOrderService(PurchaseOrderRepository orders, PurchaseOrderLineRepository lines,
                                PurchaseOrderEventRepository events, SupplierConnectionRepository connections,
                                SupplierProductRepository products, SupplierProductLinkRepository links,
                                SupplierAccess supplierAccess, ItemService items, PurchaseService purchases,
                                NetworkSupport support, Clock clock,
                                @Value("${app.business-zone:Asia/Kolkata}") String zone) {
        this.orders = orders;
        this.lines = lines;
        this.events = events;
        this.connections = connections;
        this.products = products;
        this.links = links;
        this.supplierAccess = supplierAccess;
        this.items = items;
        this.purchases = purchases;
        this.support = support;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    // ================================================================== shop

    @Transactional(readOnly = true)
    public List<ShopOrderDto> shopOrders(AuthUser caller, String status) {
        NetworkSupport.requireAny(caller, Permission.PURCHASE_ORDER_MANAGE);
        return orders.searchForShop(caller.businessId(), status(status)).stream()
                .map(o -> shopDto(o, false)).toList();
    }

    @Transactional(readOnly = true)
    public ShopOrderDto shopOrder(AuthUser caller, UUID id) {
        NetworkSupport.requireAny(caller, Permission.PURCHASE_ORDER_MANAGE);
        return shopDto(shopRow(caller, id), true);
    }

    /**
     * Places an order with a supplier the shop has an ACTIVE connection with (the supplier must still be approved).
     * Every product must be active in that supplier's catalogue and linked by the shopkeeper to a shop item. Names,
     * units, prices and the link are copied into the order lines.
     *
     * <p>The optional id is an idempotency key: sending the same request again returns the order it placed, while the
     * same id with a different connection, note or lines is refused (ORDER_ID_REUSED). The connection is locked
     * before the id is looked up, so two requests with the same id for the same connection run one after the other
     * and the second finds the first; with different connections the second insert hits the primary key and is
     * refused (409). Either way only one order and one event are written.
     */
    @Transactional
    public ShopOrderDto create(AuthUser caller, OrderInput in) {
        NetworkSupport.requireAny(caller, Permission.PURCHASE_ORDER_MANAGE);
        if (in == null || in.connectionId() == null) throw NetworkError.INVALID_REQUEST.ex();
        UUID bid = caller.businessId();
        // First statement of the transaction: the connection lock (see the class comment for the lock order).
        SupplierConnection c = connections.lockForShop(bid, in.connectionId())
                .orElseThrow(() -> ApiException.notFound("Connection"));
        if (in.id() != null) {
            PurchaseOrder existing = orders.findById(in.id()).orElse(null);
            if (existing != null) {
                if (!bid.equals(existing.getBusinessId()) || !sameRequest(existing, in)) {
                    throw NetworkError.ORDER_ID_REUSED.ex();
                }
                return shopDto(existing, true);
            }
        }
        if (!c.isActive()) throw NetworkError.CONNECTION_NOT_ACTIVE.ex();
        if (!support.org(c.getSupplierOrgId()).isApproved()) throw NetworkError.SUPPLIER_NOT_AVAILABLE.ex();
        String note = NetworkSupport.text(in.note(), 1000);
        List<OrderLineInput> wanted = in.lines() == null ? List.of() : in.lines();
        if (wanted.isEmpty()) throw NetworkError.EMPTY_ORDER.ex();
        if (wanted.size() > MAX_LINES) throw NetworkError.TOO_MANY_LINES.ex();

        UUID orderId = in.id() != null ? in.id() : UUID.randomUUID();
        Set<UUID> seen = new HashSet<>();
        List<PurchaseOrderLine> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 0;
        for (OrderLineInput li : wanted) {
            if (li == null || li.productId() == null) throw NetworkError.INVALID_REQUEST.ex();
            if (!seen.add(li.productId())) throw NetworkError.DUPLICATE_PRODUCT.ex();
            SupplierProduct product = products.findBySupplierOrgIdAndId(c.getSupplierOrgId(), li.productId())
                    .filter(SupplierProduct::isActive)
                    .orElseThrow(NetworkError.PRODUCT_NOT_AVAILABLE::ex);
            SupplierProductLink link = links.findByBusinessIdAndSupplierProductId(bid, product.getId())
                    .orElseThrow(NetworkError.PRODUCT_NOT_LINKED::ex);
            items.requireUsable(bid, link.getItemId());
            BigDecimal qty = Decimals.parse(li.quantity(), "Quantity of " + product.getNameEn(), 3, false);
            if (qty == null) throw NetworkError.INVALID_REQUEST.ex();
            // The whole order must be receivable: the quantity in the shop's unit must fit 3 decimal places.
            shopQuantity(qty, link.getUnitsPerPack());
            BigDecimal amount = qty.multiply(product.getPrice()).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount);
            rows.add(new PurchaseOrderLine(UUID.randomUUID(), bid, orderId, ++lineNo, product, link, qty, amount));
        }
        Instant now = Instant.now(clock);
        PurchaseOrder order = orders.saveAndFlush(new PurchaseOrder(orderId, bid, c.getSupplierOrgId(), c.getId(),
                note, total, rows.size(), caller.userId(), UUID.randomUUID(), now));
        lines.saveAll(rows);
        lines.flush();
        record(order, PurchaseOrderEvent.ACTOR_SHOP, caller.userId(), null, null, now);
        return shopDto(order, true);
    }

    /**
     * Whether a repeated create request is the one that placed this order: same connection, same note and the same
     * products and quantities in the same order. Names, units and prices are copied from the catalogue, not sent.
     */
    private boolean sameRequest(PurchaseOrder o, OrderInput in) {
        if (!o.getConnectionId().equals(in.connectionId())) return false;
        if (!java.util.Objects.equals(o.getNote(), NetworkSupport.text(in.note(), 1000))) return false;
        List<OrderLineInput> wanted = in.lines() == null ? List.of() : in.lines();
        List<PurchaseOrderLine> stored = lines.findByBusinessIdAndOrderIdOrderByLineNoAsc(o.getBusinessId(), o.getId());
        if (wanted.size() != stored.size()) return false;
        for (int i = 0; i < wanted.size(); i++) {
            OrderLineInput li = wanted.get(i);
            PurchaseOrderLine line = stored.get(i);
            if (li == null || !line.getSupplierProductId().equals(li.productId())) return false;
            BigDecimal qty;
            try {
                qty = Decimals.parse(li.quantity(), "Quantity", 3, false);
            } catch (ApiException e) {
                return false;
            }
            if (qty == null || qty.compareTo(line.getQuantity()) != 0) return false;
        }
        return true;
    }

    /** The shop cancels an order that has not been dispatched yet (SENT or ACCEPTED). */
    @Transactional
    public ShopOrderDto cancel(AuthUser caller, UUID id, ReasonInput in) {
        NetworkSupport.requireAny(caller, Permission.PURCHASE_ORDER_MANAGE);
        PurchaseOrder o = lockShopRow(caller, id).order();
        String reason = NetworkSupport.reason(in);
        String from = o.getStatus();
        Instant now = Instant.now(clock);
        o.cancel(reason, now);
        o = orders.saveAndFlush(o);
        record(o, PurchaseOrderEvent.ACTOR_SHOP, caller.userId(), from, reason, now);
        return shopDto(o, true);
    }

    /**
     * The shop closes a DISPATCHED order whose goods never arrived (DELIVERY_FAILED, reason required). Nothing is
     * received: no purchase, delivery or batch, and stock is unchanged. The order is final afterwards. Allowed after
     * the connection has ended, like the receipt of a dispatched order. Same lock order as every order change.
     */
    @Transactional
    public ShopOrderDto deliveryFailed(AuthUser caller, UUID id, ReasonInput in) {
        NetworkSupport.requireAny(caller, Permission.PURCHASE_ORDER_MANAGE);
        PurchaseOrder o = lockShopRow(caller, id).order();
        String reason = NetworkSupport.reason(in);
        if (reason == null) throw NetworkError.REASON_REQUIRED.ex();
        if (PurchaseOrder.RECEIVED.equals(o.getStatus())) throw NetworkError.ORDER_ALREADY_RECEIVED.ex();
        String from = o.getStatus();
        Instant now = Instant.now(clock);
        o.deliveryFailed(reason, now);
        o = orders.saveAndFlush(o);
        record(o, PurchaseOrderEvent.ACTOR_SHOP, caller.userId(), from, reason, now);
        return shopDto(o, true);
    }

    /**
     * Goods receipt (exactly one per order). Every order line must be given once with the quantity actually received
     * in the supplier's unit (0 up to the ordered quantity); it is converted to the shop unit with the link copied
     * into the order. The order's saved prices are used unless the user changes them (PURCHASE_PRICE_ENTER).
     */
    @Transactional
    public ReceiptDto receive(AuthUser caller, UUID id, ReceiptInput in) {
        NetworkSupport.requireAny(caller, Permission.PURCHASE_ORDER_MANAGE);
        UUID bid = caller.businessId();
        // The row lock makes a second, concurrent receipt wait here and then find the order already RECEIVED.
        Locked locked = lockShopRow(caller, id);
        PurchaseOrder order = locked.order();
        if (PurchaseOrder.RECEIVED.equals(order.getStatus())) throw NetworkError.ORDER_ALREADY_RECEIVED.ex();
        if (!PurchaseOrder.RECEIVABLE.contains(order.getStatus())) throw NetworkError.ORDER_STATUS.ex();
        // A dispatched order stays receivable after the connection ended (the goods are on their way).
        SupplierConnection c = locked.connection();
        if (c.getSupplierId() == null) throw NetworkError.CONTACT_NOT_LINKED.ex();
        if (in == null || in.lines() == null) throw NetworkError.RECEIPT_LINES.ex();

        List<PurchaseOrderLine> ordered = lines.findByBusinessIdAndOrderIdOrderByLineNoAsc(bid, order.getId());
        Map<UUID, ReceiptLineInput> given = new HashMap<>();
        for (ReceiptLineInput li : in.lines()) {
            if (li == null || li.lineId() == null || given.put(li.lineId(), li) != null) {
                throw NetworkError.RECEIPT_LINES.ex();
            }
        }
        if (given.size() != ordered.size()
                || !ordered.stream().allMatch(l -> given.containsKey(l.getId()))) {
            throw NetworkError.RECEIPT_LINES.ex();
        }
        String invoiceNo = NetworkSupport.text(in.invoiceNo(), 60);
        String notes = NetworkSupport.text(in.notes(), 900);
        LocalDate date = in.receivedDate() != null ? in.receivedDate() : LocalDate.now(clock.withZone(zone));
        boolean canPrice = caller.has(Permission.PURCHASE_PRICE_ENTER);

        List<LineInput> purchaseLines = new ArrayList<>();
        List<DeliveryLineInput> deliveryLines = new ArrayList<>();
        Set<UUID> savedPriceLines = new HashSet<>();
        Map<UUID, BigDecimal[]> outcome = new HashMap<>();
        for (PurchaseOrderLine line : ordered) {
            ReceiptLineInput li = given.get(line.getId());
            String name = line.getProductNameEn();
            BigDecimal qty = Decimals.parse(li.quantity(), "Received quantity of " + name, 3, true);
            if (qty == null) throw NetworkError.RECEIPT_LINES.ex();
            if (qty.compareTo(line.getQuantity()) > 0) throw NetworkError.RECEIVED_TOO_MUCH.ex();
            // The price saved in the order (what the supplier accepted) is the trusted baseline. Only a user with
            // PURCHASE_PRICE_ENTER may change it; anyone else sending a different price is refused, not ignored.
            BigDecimal price = line.getUnitPrice();
            boolean saved = true;
            BigDecimal entered = Decimals.parse(li.unitPrice(), "Price of " + name, 2, true);
            if (entered != null && entered.compareTo(price) != 0) {
                if (!canPrice) throw NetworkError.PERMISSION_DENIED.ex();
                price = entered;
                saved = false;
            }
            String batchNo = NetworkSupport.text(li.batchNo(), 60);
            outcome.put(line.getId(), new BigDecimal[]{qty, price});
            if (qty.signum() == 0) continue;

            BigDecimal shopQty = shopQuantity(qty, line.getUnitsPerPack());
            UUID purchaseLineId = UUID.randomUUID();
            if (saved) savedPriceLines.add(purchaseLineId);
            // TOTAL basis: the line amount in the supplier's unit, so no per-unit rounding happens on conversion.
            String amount = Decimals.money(qty.multiply(price));
            purchaseLines.add(new LineInput(purchaseLineId, line.getItemId(), shopQty.toPlainString(),
                    line.getItemUnitId(), PurchaseCalculator.TOTAL, amount, null, null, null));
            deliveryLines.add(new DeliveryLineInput(UUID.randomUUID(), purchaseLineId, DeliveryLine.RECEIPT,
                    shopQty.toPlainString(), null, null, batchNo, li.expiryDate()));
        }
        if (purchaseLines.isEmpty()) throw NetworkError.NOTHING_RECEIVED.ex();

        String purchaseNotes = "Purchase order " + order.getId().toString().substring(0, 8)
                + (notes == null ? "" : ": " + notes);
        PurchaseInput purchase = new PurchaseInput(c.getSupplierId(), invoiceNo, date, null, purchaseNotes, true,
                purchaseLines, new DeliveryInput(UUID.randomUUID(), null, date, null, deliveryLines));
        // The existing purchase workflow: its own validation and permission checks (PURCHASE_RECORD; and
        // PURCHASE_PRICE_ENTER for every price that is not the saved order price), the purchase, the delivery and the
        // batches. Its id is the one fixed when the order was placed (primary key: never twice).
        purchases.createWithSavedPrices(caller, order.getReceiptPurchaseId(), purchase, savedPriceLines);

        Instant now = Instant.now(clock);
        for (PurchaseOrderLine line : ordered) {
            BigDecimal[] r = outcome.get(line.getId());
            line.received(r[0], r[1]);
        }
        lines.saveAll(ordered);
        // FULL only when every line arrived in full; any short or missing line makes the receipt SHORT. Either way
        // this is the order's one final receipt and the order is closed.
        String receiptStatus = ordered.stream().allMatch(l -> PurchaseOrderLine.FULL.equals(l.receiptStatus()))
                ? PurchaseOrderLine.FULL : PurchaseOrderLine.SHORT;
        String from = order.getStatus();
        order.received(order.getReceiptPurchaseId(), receiptStatus, caller.userId(), now);
        order = orders.saveAndFlush(order);
        events.save(new PurchaseOrderEvent(order.getId(), order.getBusinessId(), order.getSupplierOrgId(),
                PurchaseOrderEvent.ACTOR_SHOP, caller.userId(), from, order.getStatus(), null, receiptStatus, now));
        orders.flush();
        return new ReceiptDto(shopDto(order, true), order.getPurchaseId(), receiptStatus);
    }

    /**
     * A quantity in the supplier's unit converted to the shop's unit (quantity x units per pack). Used when an order
     * is placed and when it is received, so an order that can be placed can always be received in full: the result
     * must fit the 3 decimal places of purchase and stock quantities (e.g. 1.5 x 0.125 = 0.1875 is refused).
     */
    static BigDecimal shopQuantity(BigDecimal quantity, BigDecimal unitsPerPack) {
        BigDecimal q = quantity.multiply(unitsPerPack).stripTrailingZeros();
        if (q.scale() > 3) throw NetworkError.QUANTITY_PRECISION.ex();
        return q.scale() < 0 ? q.setScale(0, RoundingMode.UNNECESSARY) : q;
    }

    // ================================================================== supplier

    @Transactional(readOnly = true)
    public List<SupplierOrderDto> supplierOrders(SupplierPrincipal caller, String status) {
        supplierAccess.requireApproved(caller);
        return orders.searchForSupplier(caller.orgId(), status(status)).stream()
                .map(o -> supplierDto(o, false)).toList();
    }

    @Transactional(readOnly = true)
    public SupplierOrderDto supplierOrder(SupplierPrincipal caller, UUID id) {
        supplierAccess.requireApproved(caller);
        return supplierDto(orders.findBySupplierOrgIdAndId(caller.orgId(), id)
                .orElseThrow(() -> ApiException.notFound("Order")), true);
    }

    @Transactional
    public SupplierOrderDto accept(SupplierPrincipal caller, UUID id) {
        supplierAccess.requireApproved(caller);
        Locked locked = lockSupplierRow(caller, id);
        PurchaseOrder o = locked.order();
        requireOpenConnection(locked);
        String from = o.getStatus();
        Instant now = Instant.now(clock);
        o.accept(now);
        return supplierChange(caller, o, from, null, now);
    }

    /** Rejecting needs a reason the shop can read. */
    @Transactional
    public SupplierOrderDto reject(SupplierPrincipal caller, UUID id, ReasonInput in) {
        supplierAccess.requireApproved(caller);
        PurchaseOrder o = lockSupplierRow(caller, id).order();
        String reason = NetworkSupport.reason(in);
        if (reason == null) throw NetworkError.REASON_REQUIRED.ex();
        String from = o.getStatus();
        Instant now = Instant.now(clock);
        o.reject(reason, now);
        return supplierChange(caller, o, from, reason, now);
    }

    @Transactional
    public SupplierOrderDto dispatch(SupplierPrincipal caller, UUID id, ReasonInput in) {
        supplierAccess.requireApproved(caller);
        Locked locked = lockSupplierRow(caller, id);
        PurchaseOrder o = locked.order();
        requireOpenConnection(locked);
        String note = NetworkSupport.reason(in);
        String from = o.getStatus();
        Instant now = Instant.now(clock);
        o.dispatch(now);
        return supplierChange(caller, o, from, note, now);
    }

    /**
     * Accepting or dispatching needs an ACTIVE connection. Ending a connection already cancels its SENT and ACCEPTED
     * orders under the same lock, so this only makes the rule explicit: a closed order gets ORDER_STATUS, anything
     * else on a closed connection CONNECTION_NOT_ACTIVE.
     */
    private static void requireOpenConnection(Locked locked) {
        if (PurchaseOrder.FINAL.contains(locked.order().getStatus())) throw NetworkError.ORDER_STATUS.ex();
        if (!locked.connection().isActive()) throw NetworkError.CONNECTION_NOT_ACTIVE.ex();
    }

    private SupplierOrderDto supplierChange(SupplierPrincipal caller, PurchaseOrder o, String from, String reason,
                                            Instant now) {
        o = orders.saveAndFlush(o);
        record(o, PurchaseOrderEvent.ACTOR_SUPPLIER, caller.userId(), from, reason, now);
        return supplierDto(o, true);
    }

    // ================================================================== shared

    private static String status(String status) {
        String s = NetworkSupport.text(status, 20);
        if (s != null && !PurchaseOrder.STATUSES.contains(s)) throw NetworkError.INVALID_REQUEST.ex();
        return s;
    }

    private void record(PurchaseOrder o, String actorType, UUID actorId, String from, String reason, Instant now) {
        events.save(new PurchaseOrderEvent(o.getId(), o.getBusinessId(), o.getSupplierOrgId(), actorType, actorId,
                from, o.getStatus(), reason, now));
    }

    private PurchaseOrder shopRow(AuthUser caller, UUID id) {
        return orders.findByBusinessIdAndId(caller.businessId(), id).orElseThrow(() -> ApiException.notFound("Order"));
    }

    /** An order and its connection, both locked: the connection first, then the order (one lock order everywhere). */
    private record Locked(SupplierConnection connection, PurchaseOrder order) {}

    private Locked lockShopRow(AuthUser caller, UUID id) {
        UUID bid = caller.businessId();
        UUID connectionId = orders.connectionIdForShop(bid, id).orElseThrow(() -> ApiException.notFound("Order"));
        SupplierConnection c = connections.lockForShop(bid, connectionId).orElseThrow();
        PurchaseOrder o = orders.lockForShop(bid, id).orElseThrow(() -> ApiException.notFound("Order"));
        return new Locked(c, o);
    }

    private Locked lockSupplierRow(SupplierPrincipal caller, UUID id) {
        UUID connectionId = orders.connectionIdForSupplier(caller.orgId(), id)
                .orElseThrow(() -> ApiException.notFound("Order"));
        SupplierConnection c = connections.lockForSupplier(caller.orgId(), connectionId).orElseThrow();
        PurchaseOrder o = orders.lockForSupplier(caller.orgId(), id).orElseThrow(() -> ApiException.notFound("Order"));
        return new Locked(c, o);
    }

    private List<OrderLineDto> lineDtos(PurchaseOrder o, boolean forShop) {
        return lines.findByBusinessIdAndOrderIdOrderByLineNoAsc(o.getBusinessId(), o.getId()).stream()
                .map(l -> new OrderLineDto(l.getId(), l.getLineNo(), l.getSupplierProductId(), l.getProductNameEn(),
                        l.getProductNameHi(), l.getUnit(), l.getPack(), NetworkSupport.qty(l.getQuantity()),
                        NetworkSupport.money(l.getUnitPrice()), NetworkSupport.money(l.getLineAmount()),
                        forShop ? l.getItemId() : null, forShop ? l.getItemUnitId() : null,
                        forShop ? NetworkSupport.qty(l.getUnitsPerPack()) : null,
                        NetworkSupport.qty(l.getReceivedQuantity()), NetworkSupport.money(l.getReceivedUnitPrice()),
                        NetworkSupport.qty(l.shortageQuantity()), l.receiptStatus()))
                .toList();
    }

    ShopOrderDto shopDto(PurchaseOrder o, boolean full) {
        return new ShopOrderDto(o.getId(), o.getStatus(), support.supplierSummary(o.getSupplierOrgId()),
                o.getConnectionId(), o.getNote(), NetworkSupport.money(o.getTotalAmount()), o.getLineCount(),
                o.getCreatedBy(), o.getCreatedAt(), o.getUpdatedAt(), o.getAcceptedAt(), o.getRejectedAt(),
                o.getDispatchedAt(), o.getReceivedAt(), o.getCancelledAt(), o.getDeliveryFailedAt(),
                o.getStatusReason(), o.getPurchaseId(), o.getReceiptStatus(), o.getVersion() == null ? 0 : o.getVersion(), full ? lineDtos(o, true) : List.of(),
                full ? events.findByOrderIdOrderByIdAsc(o.getId()).stream().map(e -> NetworkSupport.event(e, true))
                        .toList() : List.of());
    }

    SupplierOrderDto supplierDto(PurchaseOrder o, boolean full) {
        return new SupplierOrderDto(o.getId(), o.getStatus(), support.shopSummary(o.getBusinessId()), o.getNote(),
                NetworkSupport.money(o.getTotalAmount()), o.getLineCount(), o.getCreatedAt(), o.getUpdatedAt(),
                o.getAcceptedAt(), o.getRejectedAt(), o.getDispatchedAt(), o.getReceivedAt(), o.getCancelledAt(),
                o.getDeliveryFailedAt(), o.getStatusReason(), o.getReceiptStatus(),
                o.getVersion() == null ? 0 : o.getVersion(),
                full ? lineDtos(o, false) : List.of(),
                full ? events.findByOrderIdOrderByIdAsc(o.getId()).stream().map(e -> NetworkSupport.event(e, false))
                        .toList() : List.of());
    }
}
