package app.stocklist.network;

import app.stocklist.common.security.AuthUser;
import app.stocklist.network.NetworkDtos.ConnectionRequest;
import app.stocklist.network.NetworkDtos.ContactLinkInput;
import app.stocklist.network.NetworkDtos.DirectoryEntry;
import app.stocklist.network.NetworkDtos.ListingDto;
import app.stocklist.network.NetworkDtos.ListingInput;
import app.stocklist.network.NetworkDtos.ReasonInput;
import app.stocklist.network.NetworkDtos.ShopConnectionDto;
import app.stocklist.network.NetworkDtos.SupplierConnectionDto;
import app.stocklist.permission.Permission;
import app.stocklist.supplier.SupplierDtos.SupplierInput;
import app.stocklist.supplier.SupplierService;
import app.stocklist.supplieraccount.SupplierAccess;
import app.stocklist.supplieraccount.SupplierOrg;
import app.stocklist.supplieraccount.SupplierOrgRepository;
import app.stocklist.supplieraccount.SupplierPrincipal;
import app.stocklist.common.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier discovery and shop-supplier connections. Only the shop starts a connection; the supplier accepts or
 * declines; either side ends an active one. Every change is written to the connection's append-only history.
 * Shop callers: SUPPLIER_NETWORK_MANAGE (reading also with PURCHASE_ORDER_MANAGE), checked first. Supplier callers:
 * an APPROVED organisation (checked from the database on every call), acting only on its own rows.
 */
@Service
public class ConnectionService {
    private static final int DIRECTORY_LIMIT = 100;

    private final SupplierConnectionRepository connections;
    private final SupplierConnectionEventRepository events;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderEventRepository orderEvents;
    private final DirectoryRepository directory;
    private final SupplierOrgRepository orgs;
    private final SupplierAccess supplierAccess;
    private final SupplierService supplierContacts;
    private final NetworkSupport support;
    private final Clock clock;

    public ConnectionService(SupplierConnectionRepository connections, SupplierConnectionEventRepository events,
                             PurchaseOrderRepository orders, PurchaseOrderEventRepository orderEvents,
                             DirectoryRepository directory, SupplierOrgRepository orgs, SupplierAccess supplierAccess,
                             SupplierService supplierContacts, NetworkSupport support, Clock clock) {
        this.connections = connections;
        this.events = events;
        this.orders = orders;
        this.orderEvents = orderEvents;
        this.directory = directory;
        this.orgs = orgs;
        this.supplierAccess = supplierAccess;
        this.supplierContacts = supplierContacts;
        this.support = support;
        this.clock = clock;
    }

    // ================================================================== shop: discovery

    @Transactional(readOnly = true)
    public List<DirectoryEntry> directory(AuthUser caller, String q) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE);
        String text = NetworkSupport.text(q, 100);
        String like = text == null ? null : "%" + text.toLowerCase(Locale.ROOT) + "%";
        Map<UUID, String> mine = new HashMap<>();
        for (SupplierConnection c : connections.findByBusinessIdOrderByUpdatedAtDesc(caller.businessId())) {
            mine.put(c.getSupplierOrgId(), c.getStatus());
        }
        return directory.search(like, PageRequest.of(0, DIRECTORY_LIMIT)).stream()
                .map(o -> new DirectoryEntry(NetworkSupport.summary(o), mine.get(o.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DirectoryEntry directoryEntry(AuthUser caller, UUID orgId) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE);
        SupplierOrg o = directory.findListed(orgId).orElseThrow(() -> ApiException.notFound("Supplier"));
        String status = connections.findByBusinessIdAndSupplierOrgId(caller.businessId(), orgId)
                .map(SupplierConnection::getStatus).orElse(null);
        return new DirectoryEntry(NetworkSupport.summary(o), status);
    }

    // ================================================================== shop: connections

    @Transactional(readOnly = true)
    public List<ShopConnectionDto> shopConnections(AuthUser caller) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE, Permission.PURCHASE_ORDER_MANAGE);
        return connections.findByBusinessIdOrderByUpdatedAtDesc(caller.businessId()).stream()
                .map(c -> shopDto(c, false)).toList();
    }

    @Transactional(readOnly = true)
    public ShopConnectionDto shopConnection(AuthUser caller, UUID id) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE, Permission.PURCHASE_ORDER_MANAGE);
        return shopDto(shopRow(caller, id), true);
    }

    /** The shop asks to connect. A supplier can only be asked while it is APPROVED and listed in the directory. */
    @Transactional
    public ShopConnectionDto request(AuthUser caller, ConnectionRequest in) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE);
        if (in == null || in.supplierOrgId() == null) throw NetworkError.INVALID_REQUEST.ex();
        UUID bid = caller.businessId();
        directory.findListed(in.supplierOrgId()).orElseThrow(() -> ApiException.notFound("Supplier"));
        Instant now = Instant.now(clock);
        SupplierConnection c = connections.lockPair(bid, in.supplierOrgId()).orElse(null);
        String from;
        if (c == null) {
            from = null;
            c = new SupplierConnection(UUID.randomUUID(), bid, in.supplierOrgId(), caller.userId(), now);
        } else if (SupplierConnection.CLOSED.contains(c.getStatus())) {
            from = c.getStatus();
            c.requestAgain(caller.userId(), now);
        } else {
            throw NetworkError.CONNECTION_EXISTS.ex();
        }
        if (in.supplierId() != null) {
            linkContact(caller, c, in.supplierId(), now);
        }
        c = connections.saveAndFlush(c);
        record(c, SupplierConnectionEvent.ACTOR_SHOP, caller.userId(), from, null, now);
        return shopDto(c, true);
    }

    /** The shop withdraws a request the supplier has not answered yet. */
    @Transactional
    public ShopConnectionDto cancelRequest(AuthUser caller, UUID id) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE);
        SupplierConnection c = lockShopRow(caller, id);
        String from = c.getStatus();
        Instant now = Instant.now(clock);
        c.cancelRequest(now);
        c = connections.saveAndFlush(c);
        record(c, SupplierConnectionEvent.ACTOR_SHOP, caller.userId(), from, null, now);
        return shopDto(c, true);
    }

    @Transactional
    public ShopConnectionDto endByShop(AuthUser caller, UUID id, ReasonInput in) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE);
        SupplierConnection c = lockShopRow(caller, id);
        String reason = NetworkSupport.reason(in);
        String from = c.getStatus();
        Instant now = Instant.now(clock);
        c.end(SupplierConnection.SHOP, reason, now);
        c = connections.saveAndFlush(c);
        record(c, SupplierConnectionEvent.ACTOR_SHOP, caller.userId(), from, reason, now);
        cancelOpenOrders(c, now);
        return shopDto(c, true);
    }

    /**
     * The shopkeeper links the supplier to one of the shop's own supplier contacts (purchases are recorded against
     * those), or creates a new contact for it. Never done automatically; a contact belongs to one supplier at most.
     */
    @Transactional
    public ShopConnectionDto linkContact(AuthUser caller, UUID id, ContactLinkInput in) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE);
        SupplierConnection c = lockShopRow(caller, id);
        boolean create = in != null && Boolean.TRUE.equals(in.createContact());
        if (in == null || (in.supplierId() == null) == !create) throw NetworkError.CONTACT_CHOICE.ex();
        Instant now = Instant.now(clock);
        UUID contact = in.supplierId();
        if (create) {
            SupplierOrg o = support.org(c.getSupplierOrgId());
            contact = UUID.randomUUID();
            // Only the supplier's public name and city; the shopkeeper can add the rest to the contact later.
            supplierContacts.create(caller, contact, new SupplierInput(NetworkSupport.summary(o).name(), null, null,
                    null, null, o.getCity(), null, null, true));
        }
        linkContact(caller, c, contact, now);
        c = connections.saveAndFlush(c);
        return shopDto(c, true);
    }

    private void linkContact(AuthUser caller, SupplierConnection c, UUID contact, Instant now) {
        supplierContacts.requireUsable(caller.businessId(), contact);
        if (connections.existsByBusinessIdAndSupplierIdAndIdNot(caller.businessId(), contact, c.getId())) {
            throw NetworkError.CONTACT_IN_USE.ex();
        }
        c.linkContact(contact, now);
    }

    // ================================================================== supplier: listing and connections

    @Transactional(readOnly = true)
    public ListingDto listing(SupplierPrincipal caller) {
        SupplierOrg o = supplierAccess.requireApproved(caller);
        return new ListingDto(o.isListedInDirectory(), o.getStatus());
    }

    /** Only the approved organisation itself chooses whether shops can find it. */
    @Transactional
    public ListingDto setListing(SupplierPrincipal caller, ListingInput in) {
        supplierAccess.requireApproved(caller);
        if (in == null || in.listed() == null) throw NetworkError.INVALID_REQUEST.ex();
        SupplierOrg o = orgs.findForUpdate(caller.orgId()).orElseThrow();
        o.listInDirectory(in.listed(), Instant.now(clock));
        orgs.saveAndFlush(o);
        return new ListingDto(o.isListedInDirectory(), o.getStatus());
    }

    @Transactional(readOnly = true)
    public List<SupplierConnectionDto> supplierConnections(SupplierPrincipal caller, String status) {
        supplierAccess.requireApproved(caller);
        String s = NetworkSupport.text(status, 12);
        if (s != null && !List.of(SupplierConnection.REQUESTED, SupplierConnection.ACTIVE, SupplierConnection.DECLINED,
                SupplierConnection.CANCELLED, SupplierConnection.ENDED).contains(s)) {
            throw NetworkError.INVALID_REQUEST.ex();
        }
        List<SupplierConnection> rows = s == null
                ? connections.findBySupplierOrgIdOrderByUpdatedAtDesc(caller.orgId())
                : connections.findBySupplierOrgIdAndStatusOrderByUpdatedAtDesc(caller.orgId(), s);
        return rows.stream().map(c -> supplierDto(c, false)).toList();
    }

    @Transactional(readOnly = true)
    public SupplierConnectionDto supplierConnection(SupplierPrincipal caller, UUID id) {
        supplierAccess.requireApproved(caller);
        return supplierDto(connections.findBySupplierOrgIdAndId(caller.orgId(), id)
                .orElseThrow(() -> ApiException.notFound("Connection")), true);
    }

    @Transactional
    public SupplierConnectionDto accept(SupplierPrincipal caller, UUID id) {
        supplierAccess.requireApproved(caller);
        SupplierConnection c = lockSupplierRow(caller, id);
        String from = c.getStatus();
        Instant now = Instant.now(clock);
        c.accept(now);
        c = connections.saveAndFlush(c);
        record(c, SupplierConnectionEvent.ACTOR_SUPPLIER, caller.userId(), from, null, now);
        return supplierDto(c, true);
    }

    @Transactional
    public SupplierConnectionDto decline(SupplierPrincipal caller, UUID id, ReasonInput in) {
        supplierAccess.requireApproved(caller);
        SupplierConnection c = lockSupplierRow(caller, id);
        String reason = NetworkSupport.reason(in);
        String from = c.getStatus();
        Instant now = Instant.now(clock);
        c.decline(reason, now);
        c = connections.saveAndFlush(c);
        record(c, SupplierConnectionEvent.ACTOR_SUPPLIER, caller.userId(), from, reason, now);
        return supplierDto(c, true);
    }

    @Transactional
    public SupplierConnectionDto endBySupplier(SupplierPrincipal caller, UUID id, ReasonInput in) {
        supplierAccess.requireApproved(caller);
        SupplierConnection c = lockSupplierRow(caller, id);
        String reason = NetworkSupport.reason(in);
        String from = c.getStatus();
        Instant now = Instant.now(clock);
        c.end(SupplierConnection.SUPPLIER, reason, now);
        c = connections.saveAndFlush(c);
        record(c, SupplierConnectionEvent.ACTOR_SUPPLIER, caller.userId(), from, reason, now);
        cancelOpenOrders(c, now);
        return supplierDto(c, true);
    }

    // ================================================================== shared

    /**
     * When a connection ends, orders not yet dispatched are cancelled (recorded as a system change). Dispatched
     * orders stay receivable: the goods are already on their way.
     */
    private void cancelOpenOrders(SupplierConnection c, Instant now) {
        for (PurchaseOrder o : orders.findByBusinessIdAndConnectionIdAndStatusIn(c.getBusinessId(), c.getId(),
                PurchaseOrder.CANCELLABLE)) {
            String from = o.getStatus();
            o.cancel("The supplier connection ended", now);
            orders.save(o);
            orderEvents.save(new PurchaseOrderEvent(o.getId(), o.getBusinessId(), o.getSupplierOrgId(),
                    PurchaseOrderEvent.ACTOR_SYSTEM, null, from, o.getStatus(), "The supplier connection ended", now));
        }
        orders.flush();
    }

    private void record(SupplierConnection c, String actorType, UUID actorId, String from, String reason,
                        Instant now) {
        events.save(new SupplierConnectionEvent(c.getId(), c.getBusinessId(), c.getSupplierOrgId(), actorType,
                actorId, from, c.getStatus(), reason, now));
    }

    private SupplierConnection shopRow(AuthUser caller, UUID id) {
        return connections.findByBusinessIdAndId(caller.businessId(), id)
                .orElseThrow(() -> ApiException.notFound("Connection"));
    }

    private SupplierConnection lockShopRow(AuthUser caller, UUID id) {
        return connections.lockForShop(caller.businessId(), id)
                .orElseThrow(() -> ApiException.notFound("Connection"));
    }

    private SupplierConnection lockSupplierRow(SupplierPrincipal caller, UUID id) {
        return connections.lockForSupplier(caller.orgId(), id)
                .orElseThrow(() -> ApiException.notFound("Connection"));
    }

    ShopConnectionDto shopDto(SupplierConnection c, boolean withHistory) {
        return new ShopConnectionDto(c.getId(), c.getStatus(), support.supplierSummary(c.getSupplierOrgId()),
                c.getSupplierId(), c.getRequestedAt(), c.getRespondedAt(), c.getEndedAt(), c.getEndedBy(),
                c.getStatusReason(), c.getVersion() == null ? 0 : c.getVersion(),
                withHistory ? events.findByConnectionIdOrderByIdAsc(c.getId()).stream()
                        .map(e -> NetworkSupport.event(e, true)).toList() : List.of());
    }

    SupplierConnectionDto supplierDto(SupplierConnection c, boolean withHistory) {
        return new SupplierConnectionDto(c.getId(), c.getStatus(), support.shopSummary(c.getBusinessId()),
                c.getRequestedAt(), c.getRespondedAt(), c.getEndedAt(), c.getEndedBy(), c.getStatusReason(),
                c.getVersion() == null ? 0 : c.getVersion(),
                withHistory ? events.findByConnectionIdOrderByIdAsc(c.getId()).stream()
                        .map(e -> NetworkSupport.event(e, false)).toList() : List.of());
    }
}
