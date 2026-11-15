package app.stocklist.purchase;

import app.stocklist.common.audit.AuditService;
import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.ChangeSequencer;
import app.stocklist.common.sync.Decimals;
import app.stocklist.common.sync.VersionConflictException;
import app.stocklist.item.Item;
import app.stocklist.item.ItemService;
import app.stocklist.permission.Permission;
import app.stocklist.purchase.PurchaseDtos.DeliveryDto;
import app.stocklist.purchase.PurchaseDtos.DeliveryInput;
import app.stocklist.purchase.PurchaseDtos.DeliveryLineDto;
import app.stocklist.purchase.PurchaseDtos.DeliveryLineInput;
import app.stocklist.purchase.PurchaseDtos.LineDto;
import app.stocklist.purchase.PurchaseDtos.LineInput;
import app.stocklist.purchase.PurchaseDtos.PurchaseDto;
import app.stocklist.purchase.PurchaseDtos.PurchaseInput;
import app.stocklist.purchase.PurchaseDtos.RevisionDto;
import app.stocklist.stocklist.StockListLineRepository;
import app.stocklist.stocklist.StockListRepository;
import app.stocklist.supplier.Supplier;
import app.stocklist.supplier.SupplierRepository;
import app.stocklist.supplier.SupplierService;
import app.stocklist.unit.UnitOfMeasure;
import app.stocklist.unit.UnitService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Purchases (supplier bills) and deliveries (goods arriving). Every public method is one database transaction:
 * the purchase, its lines, an initial delivery, batch rows, catalogue prices, list receiving status and the audit
 * row are all saved together or not at all. Totals are always recalculated here; numbers sent by a phone are
 * never trusted.
 *
 * History rules: a completed purchase is never deleted (only cancelled), each edit or cancellation first stores a
 * revision with the full previous state, line prices are snapshots, and deliveries are append-only.
 */
@Service
public class PurchaseService {
    private final PurchaseRepository purchases;
    private final PurchaseLineRepository lines;
    private final DeliveryRepository deliveries;
    private final DeliveryLineRepository deliveryLines;
    private final BatchRepository batches;
    private final PurchaseRevisionRepository revisions;
    private final ReceivingService receiving;
    private final SupplierService supplierService;
    private final SupplierRepository suppliers;
    private final ItemService items;
    private final UnitService units;
    private final StockListRepository lists;
    private final StockListLineRepository listLines;
    private final ChangeSequencer seq;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public PurchaseService(PurchaseRepository purchases, PurchaseLineRepository lines, DeliveryRepository deliveries,
                           DeliveryLineRepository deliveryLines, BatchRepository batches,
                           PurchaseRevisionRepository revisions, ReceivingService receiving,
                           SupplierService supplierService, SupplierRepository suppliers, ItemService items,
                           UnitService units, StockListRepository lists, StockListLineRepository listLines,
                           ChangeSequencer seq, AuditService audit, ObjectMapper json, Clock clock) {
        this.purchases = purchases;
        this.lines = lines;
        this.deliveries = deliveries;
        this.deliveryLines = deliveryLines;
        this.batches = batches;
        this.revisions = revisions;
        this.receiving = receiving;
        this.supplierService = supplierService;
        this.suppliers = suppliers;
        this.items = items;
        this.units = units;
        this.lists = lists;
        this.listLines = listLines;
        this.seq = seq;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    public static boolean canView(AuthUser caller) {
        return caller.has(Permission.PURCHASE_HISTORY_VIEW) || caller.has(Permission.PURCHASE_RECORD)
                || caller.has(Permission.PURCHASE_EDIT) || caller.has(Permission.PURCHASE_DELETE);
    }

    // ================================================================== read

    @Transactional(readOnly = true)
    public List<PurchaseDto> search(AuthUser caller, String q, UUID supplierId, String status,
                                    java.time.LocalDate from, java.time.LocalDate to) {
        requireView(caller);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return dtos(caller, purchases.search(caller.businessId(), like, supplierId,
                status == null || status.isBlank() ? null : status, from, to));
    }

    @Transactional(readOnly = true)
    public PurchaseDto get(AuthUser caller, UUID id) {
        requireView(caller);
        return dto(caller, load(caller, id));
    }

    @Transactional(readOnly = true)
    public List<RevisionDto> revisions(AuthUser caller, UUID id) {
        requireView(caller);
        load(caller, id);
        List<RevisionDto> out = new ArrayList<>();
        for (PurchaseRevision r : revisions.findByBusinessIdAndPurchaseIdOrderByRevisionNoAsc(caller.businessId(), id)) {
            PurchaseDto before = json.readValue(r.getSnapshot(), PurchaseDto.class);
            out.add(new RevisionDto(r.getRevisionNo(), r.getChangedBy(), r.getChangedAt(), r.getReason(),
                    forViewer(caller, before)));
        }
        return out;
    }

    // ================================================================== write

    @Transactional
    public PurchaseDto create(AuthUser caller, UUID id, PurchaseInput in) {
        require(caller, Permission.PURCHASE_RECORD);
        if (purchases.existsById(id)) {
            Purchase existing = purchases.findByBusinessIdAndId(caller.businessId(), id)
                    .orElseThrow(() -> ApiException.conflict("ID_IN_USE", "This id is already used"));
            throw new VersionConflictException("VERSION_CONFLICT", "This purchase already exists",
                    dto(caller, existing), existing.getVersion());
        }
        Prepared p = prepare(caller, null, in);
        Instant now = Instant.now(clock);
        // save() returns the managed copy of a new record: keep using that one, not the object passed in.
        Purchase purchase = write(caller, new Purchase(id, caller.businessId(), caller.userId(), now,
                seq.next(caller.businessId())), p, in);
        if (in.complete()) completeNow(caller, purchase, p.lines());
        purchases.saveAndFlush(purchase);
        audit.record(caller.businessId(), caller.userId(), in.complete() ? "PURCHASE_COMPLETED" : "PURCHASE_DRAFTED",
                "PURCHASE", id, summary(purchase), caller.deviceId(), null);
        if (in.receive() != null) receive(caller, purchase, in.receive());
        receiving.refresh(caller.businessId(), id, Set.of());
        return dto(caller, load(caller, id));
    }

    @Transactional
    public PurchaseDto update(AuthUser caller, UUID id, Long baseVersion, PurchaseInput in) {
        Purchase purchase = lock(caller, id);
        VersionConflictException.check(purchase, baseVersion, dto(caller, purchase));
        if (purchase.isCancelled()) {
            throw ApiException.badRequest("PURCHASE_CANCELLED", "A cancelled purchase cannot be changed");
        }
        if (purchase.isDeleted()) purchase.restore(); // a deleted draft brought back by "keep mine"
        boolean wasCompleted = purchase.isCompleted();
        if (wasCompleted) {
            require(caller, Permission.PURCHASE_EDIT);
            if (!in.complete()) {
                throw ApiException.badRequest("ALREADY_COMPLETED", "A completed purchase cannot go back to draft");
            }
        } else if (!caller.has(Permission.PURCHASE_EDIT)
                && !(caller.has(Permission.PURCHASE_RECORD) && caller.userId().equals(purchase.getCreatedBy()))) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission to edit this purchase");
        }
        Set<UUID> listsBefore = new HashSet<>(receiving.listsOf(caller.businessId(), currentLines(purchase)));
        if (purchase.getStockListId() != null) listsBefore.add(purchase.getStockListId());
        Prepared p = prepare(caller, purchase, in);
        if (wasCompleted) keepRevision(caller, purchase, null);
        purchase.touch(caller.userId(), Instant.now(clock), seq.next(caller.businessId()));
        purchase = write(caller, purchase, p, in);
        if (!wasCompleted && in.complete()) {
            completeNow(caller, purchase, p.lines());
        } else if (wasCompleted) {
            updateCatalog(caller, purchase, p.lines());
        }
        purchases.saveAndFlush(purchase);
        audit.record(caller.businessId(), caller.userId(),
                wasCompleted ? "PURCHASE_EDITED" : in.complete() ? "PURCHASE_COMPLETED" : "PURCHASE_DRAFTED",
                "PURCHASE", id, summary(purchase), caller.deviceId(), null);
        if (in.receive() != null) receive(caller, purchase, in.receive());
        batches.touchForPurchase(caller.businessId(), id, seq.next(caller.businessId()));
        receiving.refresh(caller.businessId(), id, listsBefore);
        return dto(caller, load(caller, id));
    }

    /**
     * A draft is removed (soft delete). A completed purchase is never deleted: it is cancelled with a reason, the
     * previous state is kept as a revision, and it stops counting towards totals and list receiving.
     */
    @Transactional
    public PurchaseDto deleteOrCancel(AuthUser caller, UUID id, Long baseVersion, String reason) {
        Purchase purchase = lock(caller, id);
        VersionConflictException.check(purchase, baseVersion, dto(caller, purchase));
        Instant now = Instant.now(clock);
        if (purchase.isDraft()) {
            if (!caller.has(Permission.PURCHASE_DELETE)
                    && !(caller.has(Permission.PURCHASE_RECORD) && caller.userId().equals(purchase.getCreatedBy()))) {
                throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission to delete this draft");
            }
            purchase.softDelete(caller.userId(), now, seq.next(caller.businessId()));
            purchases.saveAndFlush(purchase);
            audit.record(caller.businessId(), caller.userId(), "PURCHASE_DRAFT_DELETED", "PURCHASE", id,
                    summary(purchase), caller.deviceId(), null);
            return dto(caller, load(caller, id));
        }
        require(caller, Permission.PURCHASE_DELETE);
        if (purchase.isCancelled()) {
            throw ApiException.badRequest("PURCHASE_CANCELLED", "This purchase is already cancelled");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "Give a reason for cancelling this purchase");
        }
        keepRevision(caller, purchase, reason.trim());
        purchase.cancel(reason.trim(), now);
        purchase.touch(caller.userId(), now, seq.next(caller.businessId()));
        purchases.saveAndFlush(purchase);
        batches.touchForPurchase(caller.businessId(), id, seq.next(caller.businessId()));
        receiving.refresh(caller.businessId(), id, Set.of());
        audit.record(caller.businessId(), caller.userId(), "PURCHASE_CANCELLED", "PURCHASE", id,
                Map.of("invoiceNo", String.valueOf(purchase.getInvoiceNo()), "reason", reason.trim()),
                caller.deviceId(), null);
        return dto(caller, load(caller, id));
    }

    /**
     * Records goods that arrived. The purchase row is locked, so two phones receiving at the same moment are
     * handled one after the other and both deliveries are kept. The same delivery id sent again (a retry) is
     * recognised and not recorded twice.
     */
    @Transactional
    public PurchaseDto addDelivery(AuthUser caller, UUID purchaseId, DeliveryInput in) {
        require(caller, Permission.PURCHASE_RECORD);
        if (in.purchaseId() != null && !in.purchaseId().equals(purchaseId)) {
            throw ApiException.badRequest("WRONG_PURCHASE", "The delivery belongs to another purchase");
        }
        Purchase purchase = lock(caller, purchaseId);
        var existing = deliveries.findById(in.id());
        if (existing.isPresent()) {
            Delivery d = existing.get();
            if (d.getBusinessId().equals(caller.businessId()) && d.getPurchaseId().equals(purchaseId)) {
                return dto(caller, purchase); // already recorded: a retried submission
            }
            throw ApiException.conflict("ID_IN_USE", "This delivery id is already used");
        }
        receive(caller, purchase, in);
        receiving.refresh(caller.businessId(), purchaseId, Set.of());
        return dto(caller, load(caller, purchaseId));
    }

    // ================================================================== internals

    private record PreparedLine(PurchaseLine line, Item item, UnitOfMeasure unit, BigDecimal quantity,
                                String basis, BigDecimal entered, BigDecimal selling, UUID listLineId,
                                boolean updateCatalog, PurchaseCalculator.Line calc) {}

    private record Prepared(List<PreparedLine> lines, List<PurchaseLine> remove, PurchaseCalculator.Totals totals) {}

    private Prepared prepare(AuthUser caller, Purchase purchase, PurchaseInput in) {
        UUID bid = caller.businessId();
        if (in.complete() && in.lines().isEmpty()) {
            throw ApiException.badRequest("EMPTY_PURCHASE", "Add at least one item before completing the purchase");
        }
        if (purchase == null || !in.supplierId().equals(purchase.getSupplierId())) {
            supplierService.requireUsable(bid, in.supplierId());
        }
        if (in.stockListId() != null && (purchase == null || !in.stockListId().equals(purchase.getStockListId()))) {
            lists.findByBusinessIdAndId(bid, in.stockListId()).filter(l -> !l.isDeleted())
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_LIST", "Unknown list"));
        }
        Map<UUID, PurchaseLine> existing = new HashMap<>();
        if (purchase != null) {
            for (PurchaseLine l : currentLines(purchase)) existing.put(l.getId(), l);
        }
        Map<UUID, BigDecimal> received = receiving.receivedByPurchaseLine(bid, existing.keySet());
        Set<UUID> seen = new HashSet<>();
        List<PreparedLine> out = new ArrayList<>();
        for (LineInput li : in.lines()) {
            if (!seen.add(li.id())) {
                throw ApiException.badRequest("DUPLICATE_LINE", "The same line appears twice");
            }
            PurchaseLine line = existing.get(li.id());
            if (line == null) {
                if (lines.existsById(li.id())) {
                    throw ApiException.badRequest("LINE_ID_IN_USE", "A line id is already used by another purchase");
                }
                line = new PurchaseLine(li.id(), bid, purchase == null ? null : purchase.getId());
            }
            boolean isNew = !existing.containsKey(li.id());
            Item item = items.requireUsable(bid, li.itemId());
            UnitOfMeasure unit = units.requireUsable(bid, li.unitId());
            String name = item.getName();
            BigDecimal qty = Decimals.parse(li.quantity(), "Quantity for " + name, 3, false);
            BigDecimal entered = Decimals.parse(li.purchasePrice(), "Purchase price for " + name,
                    PurchaseCalculator.TOTAL.equals(li.priceBasis()) ? 2 : 4, true);
            BigDecimal selling = Decimals.parse(li.sellingPrice(), "Selling price for " + name, 4, true);

            // Price permissions apply to prices this user enters or changes, not to prices already on the line.
            if (entered != null && (isNew || !same(entered, line.getEnteredAmount())
                    || !li.priceBasis().equals(line.getPriceBasis()))) {
                requirePrice(caller, Permission.PURCHASE_PRICE_ENTER, "enter purchase prices");
            }
            if (selling != null && (isNew || !same(selling, line.getSellingUnitPrice()))) {
                requirePrice(caller, Permission.SELLING_PRICE_ENTER, "enter selling prices");
            }
            BigDecimal got = received.getOrDefault(li.id(), BigDecimal.ZERO);
            if (!isNew && got.signum() != 0) {
                if (qty.compareTo(got) < 0) {
                    throw ApiException.badRequest("BELOW_RECEIVED", name + ": the billed quantity cannot be less "
                            + "than the " + Decimals.format(got) + " already received");
                }
                if (!line.getUnitId().equals(li.unitId()) || !line.getItemId().equals(li.itemId())) {
                    throw ApiException.badRequest("LINE_RECEIVED", name + ": goods were already received, so the "
                            + "item and unit cannot change");
                }
            }
            if (li.stockListItemId() != null) {
                listLines.findById(li.stockListItemId()).filter(l -> l.getBusinessId().equals(bid))
                        .orElseThrow(() -> ApiException.badRequest("UNKNOWN_LIST_LINE", "Unknown list line"));
            }
            out.add(new PreparedLine(line, item, unit, qty, li.priceBasis(), entered, selling, li.stockListItemId(),
                    li.updateCatalog() == null || li.updateCatalog(),
                    PurchaseCalculator.line(li.priceBasis(), qty, entered, selling)));
        }
        List<PurchaseLine> remove = new ArrayList<>();
        for (PurchaseLine old : existing.values()) {
            if (seen.contains(old.getId())) continue;
            if (received.getOrDefault(old.getId(), BigDecimal.ZERO).signum() != 0) {
                throw ApiException.badRequest("LINE_RECEIVED", old.getItemNameSnapshot()
                        + ": goods were already received for this line, so it cannot be removed");
            }
            remove.add(old);
        }
        return new Prepared(out, remove,
                PurchaseCalculator.totals(out.stream().map(PreparedLine::calc).toList()));
    }

    private Purchase write(AuthUser caller, Purchase draft, Prepared p, PurchaseInput in) {
        draft.apply(in.supplierId(), blank(in.invoiceNo()), in.purchaseDate(), in.stockListId(), blank(in.notes()),
                p.lines().size(), p.totals());
        Purchase purchase = purchases.saveAndFlush(draft);
        for (PurchaseLine old : p.remove()) lines.delete(old);
        int order = 0;
        for (PreparedLine pl : p.lines()) {
            PurchaseLine line = pl.line();
            // Snapshot: a line keeps the item name and unit code it was first saved with, unless its item or unit
            // is changed on purpose. Catalogue renames never reach existing lines.
            boolean sameItem = !line.isNew() && pl.item().getId().equals(line.getItemId());
            boolean sameUnit = !line.isNew() && pl.unit().getId().equals(line.getUnitId());
            line.apply(pl.item().getId(), sameItem ? line.getItemNameSnapshot() : pl.item().getName(),
                    pl.unit().getId(), sameUnit ? line.getUnitCodeSnapshot() : pl.unit().getCode(), pl.quantity(),
                    pl.basis(), pl.entered(), pl.selling(), pl.calc(), pl.listLineId(), pl.updateCatalog(), order++);
            line.attachTo(purchase.getId());
            lines.save(line);
        }
        lines.flush();
        return purchase;
    }

    private void completeNow(AuthUser caller, Purchase purchase, List<PreparedLine> prepared) {
        purchase.complete(caller.userId(), Instant.now(clock));
        updateCatalog(caller, purchase, prepared);
    }

    private void updateCatalog(AuthUser caller, Purchase purchase, List<PreparedLine> prepared) {
        for (PreparedLine pl : prepared) {
            if (pl.updateCatalog()) {
                items.recordPurchasePrices(caller, pl.item().getId(), pl.calc().unitPrice(), pl.selling(),
                        purchase.getPurchaseDate());
            }
        }
    }

    /** Stores the full current state before a completed purchase is changed or cancelled. */
    private void keepRevision(AuthUser caller, Purchase purchase, String reason) {
        PurchaseDto before = fullDto(purchase);
        purchase.nextRevision();
        revisions.save(new PurchaseRevision(caller.businessId(), purchase.getId(), purchase.getRevisionNo(),
                json.writeValueAsString(before), caller.userId(), Instant.now(clock), reason));
    }

    private void receive(AuthUser caller, Purchase purchase, DeliveryInput in) {
        UUID bid = caller.businessId();
        if (!purchase.isCompleted()) {
            throw ApiException.badRequest(purchase.isCancelled() ? "PURCHASE_CANCELLED" : "PURCHASE_NOT_COMPLETED",
                    purchase.isCancelled() ? "A cancelled purchase cannot receive goods"
                            : "Complete the purchase before receiving goods");
        }
        if (deliveries.existsById(in.id())) {
            throw ApiException.conflict("ID_IN_USE", "This delivery id is already used");
        }
        Map<UUID, PurchaseLine> billed = ReceivingService.index(currentLines(purchase), PurchaseLine::getId);
        Map<UUID, BigDecimal> received = new HashMap<>(receiving.receivedByPurchaseLine(bid, billed.keySet()));
        Delivery delivery = new Delivery(in.id(), bid, purchase.getId(), in.receivedDate(), caller.userId(),
                blank(in.notes()), Instant.now(clock));
        deliveries.saveAndFlush(delivery);
        Set<UUID> seen = new HashSet<>();
        Map<String, Object> log = new LinkedHashMap<>();
        for (DeliveryLineInput li : in.lines()) {
            if (!seen.add(li.id()) || deliveryLines.existsById(li.id())) {
                throw ApiException.badRequest("DUPLICATE_LINE", "A delivery line id is repeated or already used");
            }
            PurchaseLine pl = billed.get(li.purchaseLineId());
            if (pl == null) {
                throw ApiException.badRequest("UNKNOWN_PURCHASE_LINE", "A received line is not on this purchase");
            }
            String name = pl.getItemNameSnapshot();
            if (DeliveryLine.RECEIPT.equals(li.kind())) {
                BigDecimal qty = Decimals.parse(li.quantity(), "Received quantity for " + name, 3, false);
                DeliveryLine dl = new DeliveryLine(li.id(), bid, delivery.getId(), pl.getId(), DeliveryLine.RECEIPT,
                        qty, null, null, blank(li.batchNo()), li.expiryDate());
                deliveryLines.saveAndFlush(dl);
                // The batch id is the receipt line id, so a phone can show the batch before the server confirms it.
                batches.save(new Batch(dl.getId(), bid, pl.getItemId(), purchase.getSupplierId(), purchase.getId(),
                        pl.getId(), dl.getId(), dl.getBatchNo(), qty, pl.getUnitId(), purchase.getPurchaseDate(),
                        in.receivedDate(), li.expiryDate(), seq.next(bid)));
                received.merge(pl.getId(), qty, BigDecimal::add);
                log.put(name, Decimals.format(qty));
            } else {
                BigDecimal qty = signed(li.quantity(), "Correction for " + name);
                if (li.correctsId() == null || li.reason() == null || li.reason().isBlank()) {
                    throw ApiException.badRequest("CORRECTION_INVALID",
                            "A correction needs the receipt it fixes and a reason");
                }
                DeliveryLine original = deliveryLines.findById(li.correctsId())
                        .filter(d -> d.getBusinessId().equals(bid) && d.getPurchaseItemId().equals(pl.getId())
                                && DeliveryLine.RECEIPT.equals(d.getKind()))
                        .orElseThrow(() -> ApiException.badRequest("CORRECTION_INVALID",
                                "A correction must point at a receipt of the same line"));
                Batch batch = batches.findByBusinessIdAndDeliveryItemId(bid, original.getId()).orElseThrow();
                if (batch.getQuantity().add(qty).signum() < 0
                        || received.getOrDefault(pl.getId(), BigDecimal.ZERO).add(qty).signum() < 0) {
                    throw ApiException.badRequest("CORRECTION_INVALID", name
                            + ": the correction would make the received quantity negative");
                }
                deliveryLines.saveAndFlush(new DeliveryLine(li.id(), bid, delivery.getId(), pl.getId(),
                        DeliveryLine.CORRECTION, qty, original.getId(), li.reason().trim(), null, null));
                batch.adjust(qty, seq.next(bid));
                batches.save(batch);
                received.merge(pl.getId(), qty, BigDecimal::add);
                log.put(name + " (correction)", Decimals.format(qty));
            }
        }
        batches.flush();
        audit.record(bid, caller.userId(), "GOODS_RECEIVED", "PURCHASE", purchase.getId(), log, caller.deviceId(), null);
    }

    /** A signed decimal ("-10", "2.5") with at most 3 places, not zero. */
    private static BigDecimal signed(String value, String field) {
        String v = value == null ? "" : value.trim();
        boolean negative = v.startsWith("-");
        BigDecimal abs = Decimals.parse(negative ? v.substring(1) : v, field, 3, false);
        if (abs == null) throw ApiException.badRequest("INVALID_NUMBER", field + " is required");
        return negative ? abs.negate() : abs;
    }

    private List<PurchaseLine> currentLines(Purchase purchase) {
        return lines.findByBusinessIdAndPurchaseIdOrderBySortOrderAsc(purchase.getBusinessId(), purchase.getId());
    }

    private Purchase load(AuthUser caller, UUID id) {
        return purchases.findByBusinessIdAndId(caller.businessId(), id)
                .orElseThrow(() -> ApiException.notFound("Purchase"));
    }

    private Purchase lock(AuthUser caller, UUID id) {
        return purchases.lock(caller.businessId(), id).orElseThrow(() -> ApiException.notFound("Purchase"));
    }

    // ================================================================== dto

    public PurchaseDto dto(AuthUser caller, Purchase p) {
        return forViewer(caller, fullDto(p));
    }

    public List<PurchaseDto> dtos(AuthUser caller, List<Purchase> found) {
        return found.stream().map(p -> dto(caller, p)).toList();
    }

    public static PurchaseDto forViewer(AuthUser caller, PurchaseDto d) {
        return caller.has(Permission.EXPECTED_PROFIT_VIEW) ? d : d.withoutProfit();
    }

    private PurchaseDto fullDto(Purchase p) {
        UUID bid = p.getBusinessId();
        List<PurchaseLine> ls = currentLines(p);
        Map<UUID, BigDecimal> received = receiving.receivedByPurchaseLine(bid, ls.stream().map(PurchaseLine::getId).toList());
        List<LineDto> lineDtos = ls.stream().map(l -> {
            var pr = ReceivingCalculator.progress(l.getQuantity(), received.get(l.getId()));
            boolean below = l.getSellingUnitPrice() != null && l.getPurchaseUnitPrice() != null
                    && l.getSellingUnitPrice().compareTo(l.getPurchaseUnitPrice()) < 0;
            return new LineDto(l.getId(), l.getItemId(), l.getItemNameSnapshot(), l.getUnitId(),
                    l.getUnitCodeSnapshot(), Decimals.format(l.getQuantity()), l.getPriceBasis(),
                    Decimals.format(l.getEnteredAmount()), Decimals.format(l.getPurchaseUnitPrice()),
                    Decimals.format(l.getSellingUnitPrice()), Decimals.money(l.getTotalCost()),
                    Decimals.money(l.getExpectedSales()), Decimals.money(l.getExpectedProfit()),
                    pct(l.getMarginPercent()), pct(l.getMarkupPercent()), below, l.getStockListItemId(),
                    l.isUpdateCatalog(), l.getSortOrder(), Decimals.format(pr.received()),
                    Decimals.format(pr.remaining()), Decimals.format(pr.overBy()), pr.status());
        }).toList();
        List<Delivery> ds = deliveries.findByBusinessIdAndPurchaseIdInOrderByReceivedDateAscCreatedAtAsc(bid,
                List.of(p.getId()));
        Map<UUID, List<DeliveryLineDto>> dLines = new HashMap<>();
        if (!ds.isEmpty()) {
            for (DeliveryLine dl : deliveryLines.findByBusinessIdAndDeliveryIdIn(bid, ds.stream().map(Delivery::getId).toList())) {
                dLines.computeIfAbsent(dl.getDeliveryId(), k -> new ArrayList<>()).add(new DeliveryLineDto(dl.getId(),
                        dl.getPurchaseItemId(), dl.getKind(), Decimals.format(dl.getQuantityReceived()),
                        dl.getCorrectsId(), dl.getReason(), dl.getBatchNo(), dl.getExpiryDate()));
            }
        }
        List<DeliveryDto> deliveryDtos = ds.stream().map(d -> new DeliveryDto(d.getId(), d.getReceivedDate(),
                d.getReceivedBy(), d.getNotes(), d.getCreatedAt(), dLines.getOrDefault(d.getId(), List.of()))).toList();
        String supplierName = suppliers.findByBusinessIdAndId(bid, p.getSupplierId()).map(Supplier::getName)
                .orElse(null);
        boolean dupInvoice = p.getInvoiceNo() != null && !p.isCancelled()
                && purchases.countSameInvoice(bid, p.getSupplierId(), p.getInvoiceNo(), p.getId()) > 0;
        BigDecimal pricedSales = p.getTotalExpectedProfit() == null || p.getPricedCost() == null ? null
                : p.getPricedCost().add(p.getTotalExpectedProfit());
        return new PurchaseDto(p.getId(), p.getSupplierId(), supplierName, p.getInvoiceNo(), p.getPurchaseDate(),
                p.getReceivedDate(), p.getStockListId(), p.getNotes(), p.getStatus(), p.isOverReceived(), dupInvoice,
                p.getItemCount(), p.getRevisionNo(), Decimals.money(p.getTotalCost()),
                Decimals.money(p.getTotalExpectedSales()), Decimals.money(p.getTotalExpectedProfit()),
                pct(PurchaseCalculator.percent(p.getTotalExpectedProfit(), pricedSales)),
                pct(PurchaseCalculator.percent(p.getTotalExpectedProfit(), p.getPricedCost())),
                p.getCancelReason(), lineDtos, deliveryDtos, p.getVersion(), p.isDeleted(), p.getCreatedBy(),
                p.getCompletedAt(), p.getCreatedAt(), p.getUpdatedAt(), p.getUpdatedBy(), p.getCompletedBy(),
                p.getCancelledAt());
    }

    private static String pct(BigDecimal d) {
        return d == null ? null : d.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static Map<String, Object> summary(Purchase p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoiceNo", p.getInvoiceNo() == null ? "" : p.getInvoiceNo());
        m.put("items", p.getItemCount());
        m.put("totalCost", p.getTotalCost() == null ? "" : Decimals.money(p.getTotalCost()));
        m.put("status", p.getStatus());
        return m;
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

    private static void requirePrice(AuthUser caller, Permission p, String what) {
        if (!caller.has(p)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission to " + what);
        }
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
