package app.stocklist.network;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The relationship between one shop (business) and one supplier organisation: one row per pair for its whole life.
 * Only the shop starts it. Status changes go through the methods below, which check the allowed transitions.
 */
@Entity
@Table(name = "supplier_connection")
public class SupplierConnection {
    public static final String REQUESTED = "REQUESTED";
    public static final String ACTIVE = "ACTIVE";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";
    public static final String ENDED = "ENDED";
    /** Closed rows: only a new, explicit request by the shop opens them again (back to REQUESTED). */
    public static final Set<String> CLOSED = Set.of(DECLINED, CANCELLED, ENDED);

    public static final String SHOP = "SHOP";
    public static final String SUPPLIER = "SUPPLIER";

    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID businessId;
    private UUID supplierOrgId;
    private UUID supplierId;
    private String status;
    private String initiatedBy;
    private UUID requestedBy;
    private Instant requestedAt;
    private Instant respondedAt;
    private Instant endedAt;
    private String endedBy;
    private String statusReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected SupplierConnection() {}

    public SupplierConnection(UUID id, UUID businessId, UUID supplierOrgId, UUID requestedBy, Instant now) {
        this.id = id;
        this.businessId = businessId;
        this.supplierOrgId = supplierOrgId;
        this.status = REQUESTED;
        this.initiatedBy = SHOP;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A new request by the shop on a closed row. */
    public void requestAgain(UUID by, Instant now) {
        require(CLOSED.contains(status));
        this.status = REQUESTED;
        this.requestedBy = by;
        this.requestedAt = now;
        this.respondedAt = null;
        this.endedAt = null;
        this.endedBy = null;
        this.statusReason = null;
        this.updatedAt = now;
    }

    public void accept(Instant now) {
        require(REQUESTED.equals(status));
        this.status = ACTIVE;
        this.respondedAt = now;
        this.statusReason = null;
        this.updatedAt = now;
    }

    public void decline(String reason, Instant now) {
        require(REQUESTED.equals(status));
        this.status = DECLINED;
        this.respondedAt = now;
        this.statusReason = reason;
        this.updatedAt = now;
    }

    public void cancelRequest(Instant now) {
        require(REQUESTED.equals(status));
        this.status = CANCELLED;
        this.endedAt = now;
        this.endedBy = SHOP;
        this.updatedAt = now;
    }

    public void end(String by, String reason, Instant now) {
        require(ACTIVE.equals(status));
        this.status = ENDED;
        this.endedAt = now;
        this.endedBy = by;
        this.statusReason = reason;
        this.updatedAt = now;
    }

    public void linkContact(UUID supplierId, Instant now) {
        this.supplierId = supplierId;
        this.updatedAt = now;
    }

    private static void require(boolean allowed) {
        if (!allowed) throw NetworkError.CONNECTION_STATUS.ex();
    }

    public boolean isActive() { return ACTIVE.equals(status); }

    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public UUID getBusinessId() { return businessId; }
    public UUID getSupplierOrgId() { return supplierOrgId; }
    public UUID getSupplierId() { return supplierId; }
    public String getStatus() { return status; }
    public String getInitiatedBy() { return initiatedBy; }
    public UUID getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getEndedBy() { return endedBy; }
    public String getStatusReason() { return statusReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
