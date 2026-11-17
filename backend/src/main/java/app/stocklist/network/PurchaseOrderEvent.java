package app.stocklist.network;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One line of a purchase order's status history. Append-only: written once and never changed or deleted (the entity is immutable to Hibernate, and
 * the repository is only used to insert and read).
 */
@Entity
@Immutable
@Table(name = "purchase_order_event")
public class PurchaseOrderEvent {
    public static final String ACTOR_SHOP = "SHOP";
    public static final String ACTOR_SUPPLIER = "SUPPLIER";
    public static final String ACTOR_SYSTEM = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID orderId;
    private UUID businessId;
    private UUID supplierOrgId;
    private String actorType;
    private UUID actorId;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private String receiptStatus;
    private Instant createdAt;

    protected PurchaseOrderEvent() {}

    public PurchaseOrderEvent(UUID orderId, UUID businessId, UUID supplierOrgId, String actorType, UUID actorId,
                    String fromStatus, String toStatus, String reason, Instant createdAt) {
        this(orderId, businessId, supplierOrgId, actorType, actorId, fromStatus, toStatus, reason, null, createdAt);
    }

    /** receiptStatus (FULL or SHORT) only on the entry that marks the order RECEIVED. */
    public PurchaseOrderEvent(UUID orderId, UUID businessId, UUID supplierOrgId, String actorType, UUID actorId,
                              String fromStatus, String toStatus, String reason, String receiptStatus,
                              Instant createdAt) {
        this.orderId = orderId;
        this.businessId = businessId;
        this.supplierOrgId = supplierOrgId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.receiptStatus = receiptStatus;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getBusinessId() { return businessId; }
    public UUID getSupplierOrgId() { return supplierOrgId; }
    public String getActorType() { return actorType; }
    public UUID getActorId() { return actorId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public String getReceiptStatus() { return receiptStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
