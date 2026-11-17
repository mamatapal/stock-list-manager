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
 * One line of a connection's history. Append-only: written once and never changed or deleted (the entity is immutable to Hibernate, and
 * the repository is only used to insert and read).
 */
@Entity
@Immutable
@Table(name = "supplier_connection_event")
public class SupplierConnectionEvent {
    public static final String ACTOR_SHOP = "SHOP";
    public static final String ACTOR_SUPPLIER = "SUPPLIER";
    public static final String ACTOR_SYSTEM = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID connectionId;
    private UUID businessId;
    private UUID supplierOrgId;
    private String actorType;
    private UUID actorId;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private Instant createdAt;

    protected SupplierConnectionEvent() {}

    public SupplierConnectionEvent(UUID connectionId, UUID businessId, UUID supplierOrgId, String actorType, UUID actorId,
                    String fromStatus, String toStatus, String reason, Instant createdAt) {
        this.connectionId = connectionId;
        this.businessId = businessId;
        this.supplierOrgId = supplierOrgId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getConnectionId() { return connectionId; }
    public UUID getBusinessId() { return businessId; }
    public UUID getSupplierOrgId() { return supplierOrgId; }
    public String getActorType() { return actorType; }
    public UUID getActorId() { return actorId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
