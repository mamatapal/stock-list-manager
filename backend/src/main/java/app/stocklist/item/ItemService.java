package app.stocklist.item;

import app.stocklist.common.audit.AuditService;
import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.ChangeSequencer;
import app.stocklist.common.sync.Decimals;
import app.stocklist.common.sync.VersionConflictException;
import app.stocklist.item.ItemDtos.ItemDto;
import app.stocklist.item.ItemDtos.ItemInput;
import app.stocklist.permission.Permission;
import app.stocklist.supplier.SupplierService;
import app.stocklist.unit.UnitService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Item catalogue. Price rules: entering a purchase price needs PURCHASE_PRICE_ENTER; entering a selling price
 * on a new item needs SELLING_PRICE_ENTER; changing the selling price of an existing item needs
 * CATALOG_SELLING_PRICE_CHANGE. Purchases (Phase 4) keep their own price snapshots and never read these back.
 */
@Service
public class ItemService {
    private final ItemRepository items;
    private final UnitService units;
    private final SupplierService suppliers;
    private final ChangeSequencer seq;
    private final AuditService audit;
    private final Clock clock;

    public ItemService(ItemRepository items, UnitService units, SupplierService suppliers, ChangeSequencer seq,
                       AuditService audit, Clock clock) {
        this.items = items;
        this.units = units;
        this.suppliers = suppliers;
        this.seq = seq;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ItemDto> search(AuthUser caller, String q, String category, boolean includeInactive) {
        require(caller, Permission.ITEM_VIEW);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        String cat = category == null || category.isBlank() ? null : category.trim();
        return items.search(caller.businessId(), like, cat, includeInactive).stream().map(ItemDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<String> categories(AuthUser caller) {
        require(caller, Permission.ITEM_VIEW);
        return items.categories(caller.businessId());
    }

    @Transactional(readOnly = true)
    public ItemDto get(AuthUser caller, UUID id) {
        require(caller, Permission.ITEM_VIEW);
        return ItemDto.of(load(caller, id));
    }

    @Transactional
    public ItemDto create(AuthUser caller, UUID id, ItemInput in) {
        require(caller, Permission.ITEM_ADD);
        if (items.existsById(id)) {
            Item existing = items.findByBusinessIdAndId(caller.businessId(), id)
                    .orElseThrow(() -> ApiException.conflict("ID_IN_USE", "This id is already used"));
            throw new VersionConflictException("VERSION_CONFLICT", "This item already exists", ItemDto.of(existing),
                    existing.getVersion());
        }
        Values v = validate(caller, id, in);
        if (v.purchase() != null) require(caller, Permission.PURCHASE_PRICE_ENTER);
        if (v.selling() != null) require(caller, Permission.SELLING_PRICE_ENTER);
        Item item = new Item(id, caller.businessId(), caller.userId(), Instant.now(clock), seq.next(caller.businessId()));
        v.applyTo(item);
        items.saveAndFlush(item);
        audit.record(caller.businessId(), caller.userId(), "ITEM_CREATED", "ITEM", id, Map.of("name", item.getName()),
                caller.deviceId(), null);
        return ItemDto.of(item);
    }

    @Transactional
    public ItemDto update(AuthUser caller, UUID id, Long baseVersion, ItemInput in) {
        require(caller, Permission.ITEM_EDIT);
        Item item = load(caller, id);
        VersionConflictException.check(item, baseVersion, ItemDto.of(item));
        if (item.isDeleted()) item.restore();
        Values v = validate(caller, id, in);
        Map<String, Object> changes = new LinkedHashMap<>();
        if (!same(item.getLatestPurchasePrice(), v.purchase())) {
            require(caller, Permission.PURCHASE_PRICE_ENTER);
            changes.put("latestPurchasePrice", Decimals.format(v.purchase()));
        }
        if (!same(item.getLatestSellingPrice(), v.selling())) {
            if (!caller.has(Permission.CATALOG_SELLING_PRICE_CHANGE)) {
                throw ApiException.forbidden("PERMISSION_DENIED",
                        "You do not have permission to change the catalogue selling price");
            }
            changes.put("latestSellingPrice", Decimals.format(v.selling()));
        }
        changes.put("name", v.name());
        v.applyTo(item);
        item.touch(caller.userId(), Instant.now(clock), seq.next(caller.businessId()));
        items.saveAndFlush(item);
        audit.record(caller.businessId(), caller.userId(),
                changes.containsKey("latestSellingPrice") ? "SELLING_PRICE_CHANGED" : "ITEM_UPDATED", "ITEM", id,
                changes, caller.deviceId(), null);
        return ItemDto.of(item);
    }

    @Transactional
    public ItemDto delete(AuthUser caller, UUID id, Long baseVersion) {
        require(caller, Permission.ITEM_DELETE);
        Item item = load(caller, id);
        VersionConflictException.check(item, baseVersion, ItemDto.of(item));
        item.softDelete(caller.userId(), Instant.now(clock), seq.next(caller.businessId()));
        items.saveAndFlush(item);
        audit.record(caller.businessId(), caller.userId(), "ITEM_DELETED", "ITEM", id, Map.of("name", item.getName()),
                caller.deviceId(), null);
        return ItemDto.of(item);
    }

    /**
     * Copies a completed purchase line's prices to the item's latest prices. Ignored when the purchase is older than
     * the item's last purchase (an old bill entered late must not overwrite newer prices). The selling price is
     * copied only for users with CATALOG_SELLING_PRICE_CHANGE.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordPurchasePrices(AuthUser caller, UUID itemId, BigDecimal purchaseUnitPrice,
                                     BigDecimal sellingUnitPrice, java.time.LocalDate purchaseDate) {
        Item item = items.findByBusinessIdAndId(caller.businessId(), itemId).orElse(null);
        if (item == null || item.isDeleted()) return;
        if (item.getLastPurchaseDate() != null && purchaseDate.isBefore(item.getLastPurchaseDate())) return;
        BigDecimal selling = caller.has(Permission.CATALOG_SELLING_PRICE_CHANGE) ? sellingUnitPrice : null;
        BigDecimal buying = caller.has(Permission.PURCHASE_PRICE_ENTER) ? purchaseUnitPrice : null;
        if (item.recordPurchase(buying, selling, purchaseDate)) {
            item.touch(caller.userId(), Instant.now(clock), seq.next(caller.businessId()));
            items.saveAndFlush(item);
            if (selling != null) {
                audit.record(caller.businessId(), caller.userId(), "SELLING_PRICE_CHANGED", "ITEM", itemId,
                        Map.of("latestSellingPrice", Decimals.format(selling), "source", "purchase"),
                        caller.deviceId(), null);
            }
        }
    }

    /** For out-of-stock list lines: a live item of this business. */
    public Item requireUsable(UUID businessId, UUID id) {
        return items.findByBusinessIdAndId(businessId, id).filter(i -> !i.isDeleted())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_ITEM", "Unknown item"));
    }

    private record Values(String name, String category, UUID unitId, BigDecimal purchase, BigDecimal selling,
                          UUID supplierId, String notes, boolean active) {
        void applyTo(Item item) {
            item.apply(name, category, unitId, purchase, selling, supplierId, notes, active);
        }
    }

    private Values validate(AuthUser caller, UUID id, ItemInput in) {
        String name = in.name().trim().replaceAll("\\s+", " ");
        if (items.nameTaken(caller.businessId(), name, id)) {
            throw ApiException.conflict("DUPLICATE_ITEM_NAME", "An item called \"" + name + "\" already exists");
        }
        units.requireUsable(caller.businessId(), in.defaultUnitId());
        if (in.preferredSupplierId() != null) {
            suppliers.requireUsable(caller.businessId(), in.preferredSupplierId());
        }
        return new Values(name, blank(in.category()), in.defaultUnitId(),
                Decimals.parse(in.latestPurchasePrice(), "Purchase price", 4, true),
                Decimals.parse(in.latestSellingPrice(), "Selling price", 4, true),
                in.preferredSupplierId(), blank(in.notes()), in.active() == null || in.active());
    }

    private Item load(AuthUser caller, UUID id) {
        return items.findByBusinessIdAndId(caller.businessId(), id).orElseThrow(() -> ApiException.notFound("Item"));
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void require(AuthUser caller, Permission p) {
        if (!caller.has(p)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission for this action");
        }
    }
}
