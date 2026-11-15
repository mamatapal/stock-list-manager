package app.stocklist.sync;

import app.stocklist.common.sync.EntityType;
import app.stocklist.item.ItemDtos.ItemDto;
import app.stocklist.item.ItemDtos.ItemInput;
import app.stocklist.purchase.PurchaseDtos.DeliveryInput;
import app.stocklist.purchase.PurchaseDtos.PurchaseDto;
import app.stocklist.purchase.PurchaseDtos.PurchaseInput;
import app.stocklist.stocklist.StockListDtos.StockListDto;
import app.stocklist.stocklist.StockListDtos.StockListInput;
import app.stocklist.supplier.SupplierDtos.SupplierDto;
import app.stocklist.supplier.SupplierDtos.SupplierInput;
import app.stocklist.unit.UnitDtos.UnitDto;
import app.stocklist.unit.UnitDtos.UnitInput;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Offline sync protocol.
 *
 * Every change made on a phone is an operation with its own id (opId, a UUID). The server remembers the outcome of
 * every op id (APPLIED, CONFLICT or REJECTED) per business, so a retried upload returns the stored answer and is
 * never applied twice. RETRY outcomes (busy rows, unexpected server errors) are not remembered.
 *
 * Conflict rules (baseVersion = the server version the phone last saw):
 *  SUPPLIER, ITEM, STOCK_LIST, draft PURCHASE
 *      Whole-record versions. If someone else saved a newer version, the change is NOT applied: the phone gets
 *      CONFLICT with the server's copy and the user chooses "keep mine" (re-sent on top of the newer version) or
 *      "use theirs". A record deleted on the server is a conflict too; "keep mine" restores it with the edit.
 *      Derived fields (list receiving progress) change without a new version, so they never cause conflicts.
 *  Completed PURCHASE
 *      Same as above, and every accepted edit or cancellation first stores the full previous state as a revision,
 *      so "keep mine" never loses the other person's version. A cancelled purchase cannot be edited (REJECTED).
 *      Never deleted: DELETE cancels it (reason required) and it stays in history.
 *  DELIVERY (goods received, corrections)
 *      Append-only: never edited, never in conflict. Concurrent receipts from two phones are both kept (the purchase
 *      row is locked while each is recorded). The delivery id comes from the phone, so the same delivery sent again,
 *      even under a new op id, is recorded once. A correction that would make stock negative is REJECTED.
 *  UNIT
 *      Create-only; a duplicate code is REJECTED.
 * Permissions are checked on every operation with the sender's current permissions; refused operations are REJECTED
 * and stay on the phone for the user to fix or discard. Nothing is discarded by the server or the phone silently.
 */
public final class SyncDtos {
    private SyncDtos() {}

    public static final String UPSERT = "UPSERT";
    public static final String DELETE = "DELETE";
    public static final String APPLIED = "APPLIED";
    public static final String CONFLICT = "CONFLICT";
    public static final String REJECTED = "REJECTED";
    /** Temporary failure: nothing was saved and nothing is remembered; the phone sends the same op again later. */
    public static final String RETRY = "RETRY";

    /**
     * One change made on a phone. PURCHASE: UPSERT with purchase; DELETE deletes a draft or, with a reason, cancels
     * a completed purchase. DELIVERY: UPSERT with delivery (entityId = delivery id); deliveries are never edited.
     * opId is the idempotency key. baseVersion is the server version the user edited
     * (null when creating). Exactly one payload matching entityType is required for UPSERT.
     */
    public record PushOp(UUID opId, EntityType entityType, UUID entityId, String action, Long baseVersion,
                         SupplierInput supplier, ItemInput item, UnitInput unit, StockListInput stockList,
                         PurchaseInput purchase, DeliveryInput delivery, String reason) {}

    public record PushRequest(@NotNull @Size(max = 50, message = "At most 50 operations per push") List<PushOp> ops) {}

    /** status APPLIED (version = new server version), CONFLICT (copy = server's current record), or REJECTED. */
    public record PushResult(UUID opId, String status, String code, String message, Long version,
                             SupplierDto supplier, ItemDto item, UnitDto unit, StockListDto stockList,
                             PurchaseDto purchase) {}

    public record PushResponse(List<PushResult> results) {}

    /** Changes after `since`, deleted records included (deleted = true). Ask again with nextSince while hasMore. */
    public record PullResponse(List<SupplierDto> suppliers, List<ItemDto> items, List<UnitDto> units,
                               List<StockListDto> stockLists, List<PurchaseDto> purchases,
                               List<app.stocklist.purchase.BatchService.BatchDto> batches,
                               List<app.stocklist.people.PeopleService.PersonDto> people, long nextSince,
                               boolean hasMore, Instant serverTime,
                               /* Changes when the database is restored from a backup: phones then download everything again. */
                               String dataEpoch) {}
}
