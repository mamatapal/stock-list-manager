package app.stocklist.network;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A purchase order from one shop to one connected supplier. Status changes go only through the methods below:
 * <pre>
 *   SENT -> ACCEPTED | REJECTED           (supplier; rejecting needs a reason)
 *   ACCEPTED -> DISPATCHED                (supplier)
 *   ACCEPTED | DISPATCHED -> RECEIVED     (shop; creates the purchase, delivery and batches)
 *   SENT | ACCEPTED -> CANCELLED          (shop)
 * </pre>
 * REJECTED, CANCELLED and RECEIVED are final. Nothing here touches stock.
 */
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {
    public static final String SENT = "SENT";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String DISPATCHED = "DISPATCHED";
    public static final String RECEIVED = "RECEIVED";
    public static final String CANCELLED = "CANCELLED";
    public static final Set<String> STATUSES = Set.of(SENT, ACCEPTED, REJECTED, DISPATCHED, RECEIVED, CANCELLED);
    public static final Set<String> FINAL = Set.of(REJECTED, CANCELLED, RECEIVED);
    public static final Set<String> RECEIVABLE = Set.of(ACCEPTED, DISPATCHED);
    public static final Set<String> CANCELLABLE = Set.of(SENT, ACCEPTED);

    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID businessId;
    private UUID supplierOrgId;
    private UUID connectionId;
    private String status;
    private String note;
    private BigDecimal totalAmount;
    private int lineCount;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant acceptedAt;
    private Instant rejectedAt;
    private Instant dispatchedAt;
    private Instant receivedAt;
    private Instant cancelledAt;
    private String statusReason;
    private UUID receiptPurchaseId;
    private UUID purchaseId;
    private UUID receivedBy;
    private String receiptStatus;

    protected PurchaseOrder() {}

    public PurchaseOrder(UUID id, UUID businessId, UUID supplierOrgId, UUID connectionId, String note,
                         BigDecimal totalAmount, int lineCount, UUID createdBy, UUID receiptPurchaseId, Instant now) {
        this.id = id;
        this.businessId = businessId;
        this.supplierOrgId = supplierOrgId;
        this.connectionId = connectionId;
        this.status = SENT;
        this.note = note;
        this.totalAmount = totalAmount;
        this.lineCount = lineCount;
        this.createdBy = createdBy;
        this.receiptPurchaseId = receiptPurchaseId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void accept(Instant now) {
        require(SENT.equals(status));
        status = ACCEPTED;
        acceptedAt = now;
        updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        require(SENT.equals(status));
        status = REJECTED;
        rejectedAt = now;
        statusReason = reason;
        updatedAt = now;
    }

    public void dispatch(Instant now) {
        require(ACCEPTED.equals(status));
        status = DISPATCHED;
        dispatchedAt = now;
        updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        require(CANCELLABLE.contains(status));
        status = CANCELLED;
        cancelledAt = now;
        statusReason = reason;
        updatedAt = now;
    }

    /** The single final receipt; receiptStatus is FULL or SHORT. The order is closed for good afterwards. */
    public void received(UUID purchaseId, String receiptStatus, UUID by, Instant now) {
        if (RECEIVED.equals(status)) throw NetworkError.ORDER_ALREADY_RECEIVED.ex();
        require(RECEIVABLE.contains(status));
        this.status = RECEIVED;
        this.receiptStatus = receiptStatus;
        this.purchaseId = purchaseId;
        this.receivedBy = by;
        this.receivedAt = now;
        this.updatedAt = now;
    }

    private static void require(boolean allowed) {
        if (!allowed) throw NetworkError.ORDER_STATUS.ex();
    }

    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public UUID getBusinessId() { return businessId; }
    public UUID getSupplierOrgId() { return supplierOrgId; }
    public UUID getConnectionId() { return connectionId; }
    public String getStatus() { return status; }
    public String getNote() { return note; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public int getLineCount() { return lineCount; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getStatusReason() { return statusReason; }
    public UUID getReceiptPurchaseId() { return receiptPurchaseId; }
    public UUID getPurchaseId() { return purchaseId; }
    public String getReceiptStatus() { return receiptStatus; }
    public UUID getReceivedBy() { return receivedBy; }
}
