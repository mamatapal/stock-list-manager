package app.stocklist.stocklist;

import app.stocklist.common.audit.AuditService;
import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.ChangeSequencer;
import app.stocklist.common.sync.Decimals;
import app.stocklist.common.sync.VersionConflictException;
import app.stocklist.item.Item;
import app.stocklist.item.ItemService;
import app.stocklist.permission.Permission;
import app.stocklist.purchase.ReceivingService;
import app.stocklist.stocklist.StockListDtos.LineInput;
import app.stocklist.stocklist.StockListDtos.StockListDto;
import app.stocklist.stocklist.StockListDtos.StockListInput;
import app.stocklist.supplier.SupplierService;
import app.stocklist.unit.UnitService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Out-of-stock lists. A list and its lines are saved as one unit with one version, so two people editing the
 * same list are detected as a conflict instead of silently mixing lines. Totals are always recomputed here
 * from quantity, price and price basis; totals sent by a phone are ignored.
 */
@Service
public class StockListService {
    private final StockListRepository lists;
    private final StockListLineRepository lines;
    private final SupplierService suppliers;
    private final ItemService items;
    private final UnitService units;
    private final ChangeSequencer seq;
    private final AuditService audit;
    private final Clock clock;
    private final ReceivingService receiving;

    public StockListService(StockListRepository lists, StockListLineRepository lines, SupplierService suppliers,
                            ItemService items, UnitService units, ChangeSequencer seq, AuditService audit,
                            Clock clock, ReceivingService receiving) {
        this.receiving = receiving;
        this.lists = lists;
        this.lines = lines;
        this.suppliers = suppliers;
        this.items = items;
        this.units = units;
        this.seq = seq;
        this.audit = audit;
        this.clock = clock;
    }

    /** Anyone who works with lists or records purchases may see them. */
    public static boolean canView(AuthUser caller) {
        return caller.has(Permission.LIST_CREATE) || caller.has(Permission.LIST_EDIT)
                || caller.has(Permission.LIST_DELETE) || caller.has(Permission.PURCHASE_RECORD);
    }

    @Transactional(readOnly = true)
    public List<StockListDto> search(AuthUser caller, String q, UUID supplierId, String status, LocalDate from,
                                     LocalDate to) {
        requireView(caller);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        List<StockList> found = lists.search(caller.businessId(), like, supplierId,
                status == null || status.isBlank() ? null : status, from, to);
        return withLines(caller.businessId(), found);
    }

    @Transactional(readOnly = true)
    public StockListDto get(AuthUser caller, UUID id) {
        requireView(caller);
        return dto(load(caller, id));
    }

    @Transactional
    public StockListDto create(AuthUser caller, UUID id, StockListInput in) {
        require(caller, Permission.LIST_CREATE);
        if (lists.existsById(id)) {
            StockList existing = lists.findByBusinessIdAndId(caller.businessId(), id)
                    .orElseThrow(() -> ApiException.conflict("ID_IN_USE", "This id is already used"));
            throw new VersionConflictException("VERSION_CONFLICT", "This list already exists", dto(existing),
                    existing.getVersion());
        }
        Prepared p = prepare(caller, null, in);
        StockList list = new StockList(id, caller.businessId(), caller.userId(), Instant.now(clock),
                seq.next(caller.businessId()));
        write(list, p, in);
        audit.record(caller.businessId(), caller.userId(), "LIST_CREATED", "STOCK_LIST", id,
                Map.of("title", list.getTitle(), "lines", in.lines().size()), caller.deviceId(), null);
        return dto(load(caller, id));
    }

    @Transactional
    public StockListDto update(AuthUser caller, UUID id, Long baseVersion, StockListInput in) {
        require(caller, Permission.LIST_EDIT);
        StockList list = load(caller, id);
        VersionConflictException.check(list, baseVersion, dto(list));
        if (list.isDeleted()) list.restore();
        Prepared p = prepare(caller, list.getId(), in);
        list.touch(caller.userId(), Instant.now(clock), seq.next(caller.businessId()));
        write(list, p, in);
        audit.record(caller.businessId(), caller.userId(), "LIST_UPDATED", "STOCK_LIST", id,
                Map.of("title", list.getTitle(), "lines", in.lines().size(), "status", list.getStatus()),
                caller.deviceId(), null);
        // A changed requested quantity can change how much is still to come.
        receiving.refreshList(caller.businessId(), id);
        return dto(load(caller, id));
    }

    @Transactional
    public StockListDto delete(AuthUser caller, UUID id, Long baseVersion) {
        require(caller, Permission.LIST_DELETE);
        StockList list = load(caller, id);
        VersionConflictException.check(list, baseVersion, dto(list));
        list.softDelete(caller.userId(), Instant.now(clock), seq.next(caller.businessId()));
        lists.saveAndFlush(list);
        audit.record(caller.businessId(), caller.userId(), "LIST_DELETED", "STOCK_LIST", id,
                Map.of("title", list.getTitle()), caller.deviceId(), null);
        return dto(list);
    }

    /** Copies a list (header, notes and lines) into a new draft dated today. */
    @Transactional
    public StockListDto duplicate(AuthUser caller, UUID sourceId, UUID newId) {
        require(caller, Permission.LIST_CREATE);
        StockList src = load(caller, sourceId);
        if (src.isDeleted()) throw ApiException.notFound("List");
        List<LineInput> copied = new ArrayList<>();
        for (StockListLine l : lines.findByBusinessIdAndStockListIdOrderBySortOrderAsc(caller.businessId(), sourceId)) {
            copied.add(new LineInput(UUID.randomUUID(), l.getItemId(), l.getItemName(), l.getDescription(),
                    Decimals.format(l.getQuantity()), l.getUnitId(), l.getPriceBasis(),
                    Decimals.format(l.getEnteredPrice())));
        }
        return create(caller, newId, new StockListInput("Copy of " + src.getTitle(), LocalDate.now(clock),
                src.getSupplierId(), src.getNotes(), StockList.DRAFT, copied));
    }

    // ------------------------------------------------------------------ internals

    /** Everything validated and calculated, before any row is written (so queries cannot flush half a change). */
    private record Prepared(List<StockListLine> keep, List<StockListLine> remove, BigDecimal total) {}

    private Prepared prepare(AuthUser caller, UUID listId, StockListInput in) {
        UUID bid = caller.businessId();
        if (StockList.SAVED.equals(in.status()) && in.lines().isEmpty()) {
            throw ApiException.badRequest("EMPTY_LIST", "Add at least one item, or save it as a draft");
        }
        if (in.supplierId() != null) {
            suppliers.requireUsable(bid, in.supplierId());
        }
        Map<UUID, StockListLine> existing = new HashMap<>();
        if (listId != null) {
            for (StockListLine l : lines.findByBusinessIdAndStockListIdOrderBySortOrderAsc(bid, listId)) {
                existing.put(l.getId(), l);
            }
        }
        Set<UUID> seen = new HashSet<>();
        List<BigDecimal> totals = new ArrayList<>();
        List<StockListLine> keep = new ArrayList<>();
        int order = 0;
        for (LineInput li : in.lines()) {
            if (!seen.add(li.id())) {
                throw ApiException.badRequest("DUPLICATE_LINE", "The same line appears twice");
            }
            StockListLine line = existing.get(li.id());
            if (line == null) {
                if (lines.existsById(li.id())) {
                    throw ApiException.badRequest("LINE_ID_IN_USE", "A line id is already used by another list");
                }
                line = new StockListLine(li.id(), bid, listId);
            }
            String name = li.itemName() == null ? null : li.itemName().trim();
            if (li.itemId() != null) {
                Item item = items.requireUsable(bid, li.itemId());
                if (name == null || name.isEmpty()) name = item.getName();
            }
            if (name == null || name.isEmpty()) {
                throw ApiException.badRequest("ITEM_NAME_REQUIRED", "Every line needs an item name");
            }
            units.requireUsable(bid, li.unitId());
            BigDecimal qty = Decimals.parse(li.quantity(), "Quantity for " + name, 3, false);
            BigDecimal price = Decimals.parse(li.enteredPrice(), "Price for " + name,
                    StockListCalculator.TOTAL.equals(li.priceBasis()) ? 2 : 4, true);
            BigDecimal lineTotal = StockListCalculator.lineTotal(li.priceBasis(), qty, price);
            totals.add(lineTotal);
            line.apply(li.itemId(), name, blank(li.description()), qty, li.unitId(), li.priceBasis(), price, lineTotal,
                    order++);
            keep.add(line);
        }
        List<StockListLine> remove = existing.values().stream().filter(l -> !seen.contains(l.getId())).toList();
        Set<UUID> linked = receiving.linkedListLines(bid, remove.stream().map(StockListLine::getId).toList());
        if (!linked.isEmpty()) {
            String name = remove.stream().filter(l -> linked.contains(l.getId())).findFirst().orElseThrow().getItemName();
            throw ApiException.badRequest("LINE_PURCHASED", name + " is linked to a purchase, so it cannot be removed "
                    + "from the list");
        }
        return new Prepared(keep, remove, StockListCalculator.listTotal(totals));
    }

    private void write(StockList list, Prepared p, StockListInput in) {
        list.apply(in.title().trim(), in.listDate(), in.supplierId(), blank(in.notes()), in.status(), p.total());
        lists.saveAndFlush(list);
        for (StockListLine old : p.remove()) {
            lines.delete(old);
        }
        for (StockListLine line : p.keep()) {
            line.attachTo(list.getId());
            lines.save(line);
        }
        lines.flush();
    }

    private StockList load(AuthUser caller, UUID id) {
        return lists.findByBusinessIdAndId(caller.businessId(), id).orElseThrow(() -> ApiException.notFound("List"));
    }

    public StockListDto dto(StockList list) {
        List<StockListLine> ls = lines.findByBusinessIdAndStockListIdOrderBySortOrderAsc(list.getBusinessId(),
                list.getId());
        return StockListDto.of(list, ls, receiving.forListLines(list.getBusinessId(), ls));
    }

    /** Loads the lines of many lists in one query (for search results and pull). */
    public List<StockListDto> withLines(UUID businessId, List<StockList> found) {
        if (found.isEmpty()) return List.of();
        Map<UUID, List<StockListLine>> byList = new HashMap<>();
        for (StockListLine l : lines.findByBusinessIdAndStockListIdInOrderBySortOrderAsc(businessId,
                found.stream().map(StockList::getId).toList())) {
            byList.computeIfAbsent(l.getStockListId(), k -> new ArrayList<>()).add(l);
        }
        Map<UUID, ReceivingService.ListLineProgress> progress = receiving.forListLines(businessId,
                byList.values().stream().flatMap(List::stream).toList());
        return found.stream().map(s -> StockListDto.of(s, byList.getOrDefault(s.getId(), List.of()), progress))
                .toList();
    }

    private static void requireView(AuthUser caller) {
        if (!canView(caller)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission for this action");
        }
    }

    private static void require(AuthUser caller, Permission p) {
        if (!caller.has(p)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission for this action");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
