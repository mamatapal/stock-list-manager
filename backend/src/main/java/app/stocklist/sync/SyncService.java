package app.stocklist.sync;

import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.EntityType;
import app.stocklist.common.sync.SyncEntity;
import app.stocklist.common.sync.VersionConflictException;
import app.stocklist.item.ItemDtos.ItemDto;
import app.stocklist.item.ItemRepository;
import app.stocklist.item.ItemService;
import app.stocklist.permission.Permission;
import app.stocklist.purchase.Batch;
import app.stocklist.purchase.DeliveryRepository;
import app.stocklist.purchase.Purchase;
import app.stocklist.purchase.PurchaseDtos.PurchaseDto;
import app.stocklist.purchase.PurchaseRepository;
import app.stocklist.purchase.PurchaseService;
import app.stocklist.stocklist.StockListDtos.StockListDto;
import app.stocklist.stocklist.StockListRepository;
import app.stocklist.stocklist.StockListService;
import app.stocklist.supplier.SupplierDtos.SupplierDto;
import app.stocklist.supplier.SupplierRepository;
import app.stocklist.supplier.SupplierService;
import app.stocklist.sync.SyncDtos.PullResponse;
import app.stocklist.sync.SyncDtos.PushOp;
import app.stocklist.sync.SyncDtos.PushResult;
import app.stocklist.unit.UnitDtos.UnitDto;
import app.stocklist.unit.UnitRepository;
import app.stocklist.unit.UnitService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Push: each operation runs in its own transaction through the same services the REST API uses, so every
 * permission, validation and business-isolation rule applies identically. The outcome is stored in the
 * idempotency ledger; a retried operation returns the stored outcome instead of being applied again.
 *
 * Pull: changed records after a change number, filtered by what the caller may see.
 */
@Service
public class SyncService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SyncService.class);
    static final int MAX_PULL = 500;

    private final SupplierService supplierService;
    private final ItemService itemService;
    private final UnitService unitService;
    private final StockListService listService;
    private final SupplierRepository suppliers;
    private final ItemRepository items;
    private final UnitRepository units;
    private final StockListRepository lists;
    private final SyncOperationRepository ledger;
    private final Validator validator;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final PurchaseService purchaseService;
    private final PurchaseRepository purchases;
    private final DeliveryRepository deliveries;
    private final app.stocklist.purchase.BatchRepository batches;
    private final app.stocklist.purchase.BatchService batchService;
    private final app.stocklist.people.PeopleService people;

    private final DataEpoch dataEpoch;

    public SyncService(SupplierService supplierService, ItemService itemService, UnitService unitService,
                       StockListService listService, SupplierRepository suppliers, ItemRepository items,
                       UnitRepository units, StockListRepository lists, SyncOperationRepository ledger,
                       Validator validator, TransactionTemplate tx, Clock clock, PurchaseService purchaseService,
                       PurchaseRepository purchases, DeliveryRepository deliveries,
                       app.stocklist.purchase.BatchRepository batches, app.stocklist.purchase.BatchService batchService,
                       app.stocklist.people.PeopleService people, DataEpoch dataEpoch) {
        this.dataEpoch = dataEpoch;
        this.batches = batches;
        this.batchService = batchService;
        this.people = people;
        this.purchaseService = purchaseService;
        this.purchases = purchases;
        this.deliveries = deliveries;
        this.supplierService = supplierService;
        this.itemService = itemService;
        this.unitService = unitService;
        this.listService = listService;
        this.suppliers = suppliers;
        this.items = items;
        this.units = units;
        this.lists = lists;
        this.ledger = ledger;
        this.validator = validator;
        this.tx = tx;
        this.clock = clock;
    }

    // ================================================================== push

    public List<PushResult> push(AuthUser caller, List<PushOp> ops) {
        List<PushResult> results = new ArrayList<>();
        for (PushOp op : ops) {
            results.add(pushOne(caller, op));
        }
        return results;
    }

    private PushResult pushOne(AuthUser caller, PushOp op) {
        if (op == null || op.opId() == null || op.entityType() == null || op.entityId() == null
                || !(SyncDtos.UPSERT.equals(op.action()) || SyncDtos.DELETE.equals(op.action()))) {
            return rejected(op == null ? null : op.opId(), "INVALID_OPERATION",
                    "opId, entityType, entityId and action (UPSERT or DELETE) are required");
        }
        var replay = ledger.findByBusinessIdAndOpId(caller.businessId(), op.opId());
        if (replay.isPresent()) {
            return fromLedger(caller, replay.get());
        }
        String invalid = validatePayload(op);
        if (invalid != null) {
            return record(caller, op, rejected(op.opId(), "VALIDATION_FAILED", invalid));
        }
        try {
            PushResult applied = tx.execute(status -> {
                PushResult r = apply(caller, op);
                ledger.save(new SyncOperation(caller.businessId(), op.opId(), caller.userId(), caller.deviceId(),
                        op.entityType().name(), op.entityId(), op.action(), SyncDtos.APPLIED, null, null, r.version(),
                        Instant.now(clock)));
                return r;
            });
            return applied;
        } catch (VersionConflictException e) {
            return record(caller, op, withCopy(new PushResult(op.opId(), SyncDtos.CONFLICT, e.code(), e.getMessage(),
                    e.currentVersion(), null, null, null, null, null), e.current()));
        } catch (ApiException e) {
            return record(caller, op, rejected(op.opId(), e.code(), e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            // Either a concurrent retry of this same op (ledger unique key) or a constraint the checks missed.
            var raced = ledger.findByBusinessIdAndOpId(caller.businessId(), op.opId());
            if (raced.isPresent()) return fromLedger(caller, raced.get());
            return record(caller, op, rejected(op.opId(), "CONFLICT", "This change conflicts with existing data"));
        } catch (org.springframework.dao.OptimisticLockingFailureException
                 | org.springframework.dao.PessimisticLockingFailureException e) {
            // Another phone changed the same rows at the same moment: nothing was saved; the phone sends it again.
            return retry(op.opId(), "BUSY", "The record was busy; this change will be sent again");
        } catch (RuntimeException e) {
            // An unexpected failure on one change must not block the others or be remembered as a final answer:
            // nothing was saved (the operation's transaction rolled back) and the phone may try again later.
            log.error("Sync operation {} ({} {}) failed", op.opId(), op.entityType(), op.action(), e);
            return retry(op.opId(), "SERVER_ERROR", "The server could not process this change; it will be tried again");
        }
    }

    /** Not stored in the ledger, so the same op id can succeed later. */
    private static PushResult retry(UUID opId, String code, String message) {
        return new PushResult(opId, SyncDtos.RETRY, code, message, null, null, null, null, null, null);
    }

    private PushResult apply(AuthUser caller, PushOp op) {
        boolean create = op.baseVersion() == null;
        boolean delete = SyncDtos.DELETE.equals(op.action());
        return switch (op.entityType()) {
            case SUPPLIER -> {
                SupplierDto d = delete ? supplierService.delete(caller, op.entityId(), op.baseVersion())
                        : create ? supplierService.create(caller, op.entityId(), op.supplier())
                        : supplierService.update(caller, op.entityId(), op.baseVersion(), op.supplier());
                yield new PushResult(op.opId(), SyncDtos.APPLIED, null, null, d.version(), d, null, null, null, null);
            }
            case ITEM -> {
                ItemDto d = delete ? itemService.delete(caller, op.entityId(), op.baseVersion())
                        : create ? itemService.create(caller, op.entityId(), op.item())
                        : itemService.update(caller, op.entityId(), op.baseVersion(), op.item());
                yield new PushResult(op.opId(), SyncDtos.APPLIED, null, null, d.version(), null, d, null, null, null);
            }
            case UNIT -> {
                if (!delete && !create) {
                    throw ApiException.badRequest("NOT_SUPPORTED", "Units cannot be edited; add a new one instead");
                }
                UnitDto d = delete ? unitService.delete(caller, op.entityId(), op.baseVersion())
                        : unitService.create(caller, op.entityId(), op.unit());
                yield new PushResult(op.opId(), SyncDtos.APPLIED, null, null, d.version(), null, null, d, null, null);
            }
            case STOCK_LIST -> {
                StockListDto d = delete ? listService.delete(caller, op.entityId(), op.baseVersion())
                        : create ? listService.create(caller, op.entityId(), op.stockList())
                        : listService.update(caller, op.entityId(), op.baseVersion(), op.stockList());
                yield new PushResult(op.opId(), SyncDtos.APPLIED, null, null, d.version(), null, null, null, d, null);
            }
            case PURCHASE -> {
                PurchaseDto d = delete ? purchaseService.deleteOrCancel(caller, op.entityId(), op.baseVersion(), op.reason())
                        : create ? purchaseService.create(caller, op.entityId(), op.purchase())
                        : purchaseService.update(caller, op.entityId(), op.baseVersion(), op.purchase());
                yield new PushResult(op.opId(), SyncDtos.APPLIED, null, null, d.version(), null, null, null, null, d);
            }
            case DELIVERY -> {
                if (delete || !create) {
                    throw ApiException.badRequest("NOT_SUPPORTED",
                            "Deliveries cannot be edited or deleted; record a correction instead");
                }
                if (!op.entityId().equals(op.delivery().id()) || op.delivery().purchaseId() == null) {
                    throw ApiException.badRequest("INVALID_OPERATION", "entityId must be the delivery id, and "
                            + "purchaseId is required");
                }
                PurchaseDto d = purchaseService.addDelivery(caller, op.delivery().purchaseId(), op.delivery());
                yield new PushResult(op.opId(), SyncDtos.APPLIED, null, null, d.version(), null, null, null, null, d);
            }
        };
    }

    /** Payload present for UPSERT and passes the same Bean Validation rules as the REST API. */
    private String validatePayload(PushOp op) {
        if (SyncDtos.DELETE.equals(op.action())) {
            return op.baseVersion() == null ? "baseVersion is required to delete" : null;
        }
        Object payload = switch (op.entityType()) {
            case SUPPLIER -> op.supplier();
            case ITEM -> op.item();
            case UNIT -> op.unit();
            case STOCK_LIST -> op.stockList();
            case PURCHASE -> op.purchase();
            case DELIVERY -> op.delivery();
        };
        if (payload == null) {
            return "Missing " + op.entityType().name().toLowerCase() + " data";
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(payload);
        if (violations.isEmpty()) return null;
        ConstraintViolation<Object> v = violations.iterator().next();
        return v.getPropertyPath() + ": " + v.getMessage();
    }

    private PushResult record(AuthUser caller, PushOp op, PushResult r) {
        try {
            tx.executeWithoutResult(status -> ledger.save(new SyncOperation(caller.businessId(), op.opId(),
                    caller.userId(), caller.deviceId(), op.entityType().name(), op.entityId(), op.action(), r.status(),
                    r.code(), r.message(), r.version(), Instant.now(clock))));
        } catch (DataIntegrityViolationException e) {
            var raced = ledger.findByBusinessIdAndOpId(caller.businessId(), op.opId());
            if (raced.isPresent()) return fromLedger(caller, raced.get());
        }
        return r;
    }

    private PushResult fromLedger(AuthUser caller, SyncOperation s) {
        PushResult r = new PushResult(s.getOpId(), s.getResultStatus(), s.getResultCode(), s.getResultMessage(),
                s.getResultVersion(), null, null, null, null, null);
        if (!SyncDtos.REJECTED.equals(s.getResultStatus())) {
            // Attach the record as it is now, so the phone can settle its copy.
            Object current = tx.execute(status -> currentCopy(caller, EntityType.valueOf(s.getEntityType()),
                    s.getEntityId()));
            r = withCopy(r, current);
        }
        return r;
    }

    private Object currentCopy(AuthUser caller, EntityType type, java.util.UUID id) {
        return switch (type) {
            case SUPPLIER -> suppliers.findByBusinessIdAndId(caller.businessId(), id).map(SupplierDto::of).orElse(null);
            case ITEM -> items.findByBusinessIdAndId(caller.businessId(), id).map(ItemDto::of).orElse(null);
            case UNIT -> units.findByBusinessIdAndId(caller.businessId(), id).map(UnitDto::of).orElse(null);
            case STOCK_LIST -> lists.findByBusinessIdAndId(caller.businessId(), id).map(listService::dto).orElse(null);
            case PURCHASE -> purchases.findByBusinessIdAndId(caller.businessId(), id)
                    .map(p -> purchaseService.dto(caller, p)).orElse(null);
            case DELIVERY -> deliveries.findByBusinessIdAndId(caller.businessId(), id)
                    .flatMap(d -> purchases.findByBusinessIdAndId(caller.businessId(), d.getPurchaseId()))
                    .map(p -> purchaseService.dto(caller, p)).orElse(null);
        };
    }

    private static PushResult withCopy(PushResult r, Object copy) {
        return new PushResult(r.opId(), r.status(), r.code(), r.message(), r.version(),
                copy instanceof SupplierDto s ? s : null, copy instanceof ItemDto i ? i : null,
                copy instanceof UnitDto u ? u : null, copy instanceof StockListDto l ? l : null,
                copy instanceof PurchaseDto p ? p : null);
    }

    private static PushResult rejected(java.util.UUID opId, String code, String message) {
        return new PushResult(opId, SyncDtos.REJECTED, code, message, null, null, null, null, null, null);
    }

    // ================================================================== pull

    @Transactional(readOnly = true)
    public PullResponse pull(AuthUser caller, long since, int limit) {
        int n = Math.max(1, Math.min(limit, MAX_PULL));
        PageRequest page = PageRequest.of(0, n);
        var bid = caller.businessId();
        List<? extends SyncEntity> sup = caller.has(Permission.SUPPLIER_VIEW)
                ? suppliers.findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(bid, since, page) : List.of();
        List<? extends SyncEntity> itm = caller.has(Permission.ITEM_VIEW)
                ? items.findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(bid, since, page) : List.of();
        List<? extends SyncEntity> uni = units.findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(bid, since, page);
        List<? extends SyncEntity> lst = StockListService.canView(caller)
                ? lists.findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(bid, since, page) : List.of();
        List<? extends SyncEntity> pur = PurchaseService.canView(caller)
                ? purchases.findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(bid, since, page) : List.of();
        List<Batch> bat = caller.has(Permission.EXPIRY_VIEW)
                ? batches.findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(bid, since, page) : List.of();

        // A table that returned a full page may have more rows; everything up to the smallest "last row" of such
        // tables is complete in every table, so that is where this page ends.
        long cutoff = Long.MAX_VALUE;
        boolean hasMore = false;
        long maxSeen = since;
        List<List<Long>> seqs = new java.util.ArrayList<>();
        for (List<? extends SyncEntity> rows : List.of(sup, itm, uni, lst, pur)) {
            seqs.add(rows.stream().map(SyncEntity::getChangeSeq).toList());
        }
        seqs.add(bat.stream().map(Batch::getChangeSeq).toList());
        for (List<Long> rows : seqs) {
            if (!rows.isEmpty()) {
                long last = rows.get(rows.size() - 1);
                maxSeen = Math.max(maxSeen, last);
                if (rows.size() == n) {
                    hasMore = true;
                    cutoff = Math.min(cutoff, last);
                }
            }
        }
        long end = hasMore ? cutoff : maxSeen;
        return new PullResponse(
                upTo(sup, end, e -> SupplierDto.of((app.stocklist.supplier.Supplier) e)),
                upTo(itm, end, e -> ItemDto.of((app.stocklist.item.Item) e)),
                upTo(uni, end, e -> UnitDto.of((app.stocklist.unit.UnitOfMeasure) e)),
                listService.withLines(bid, lst.stream().filter(e -> e.getChangeSeq() <= end)
                        .map(e -> (app.stocklist.stocklist.StockList) e).toList()),
                // Profit fields are removed for users without EXPECTED_PROFIT_VIEW, so they never reach that phone.
                purchaseService.dtos(caller, pur.stream().filter(e -> e.getChangeSeq() <= end)
                        .map(e -> (Purchase) e).toList()),
                batchService.dtos(bid, bat.stream().filter(b -> b.getChangeSeq() <= end).toList(), batchService.today()),
                // Names for "created by" labels: small, so sent with every pull.
                people.list(caller),
                end, hasMore, Instant.now(clock), dataEpoch.current());
    }

    private static <T> List<T> upTo(List<? extends SyncEntity> rows, long end, Function<SyncEntity, T> map) {
        return rows.stream().filter(e -> e.getChangeSeq() <= end).map(map).toList();
    }
}
