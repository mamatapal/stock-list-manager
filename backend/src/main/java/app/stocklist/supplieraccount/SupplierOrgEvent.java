package app.stocklist.supplieraccount;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One line of a supplier's history (registration, verification, review decisions). Insert-only. */
@Entity
@Table(name = "supplier_org_event")
public class SupplierOrgEvent {
    public static final String REGISTERED = "REGISTERED";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String DETAILS_UPDATED = "DETAILS_UPDATED";
    public static final String RESUBMITTED = "RESUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String REINSTATED = "REINSTATED";
    // Account security (recovery is auditable: who asked, from where, and what it changed)
    public static final String REGISTRATION_ATTEMPT = "REGISTRATION_ATTEMPT";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    /** The email was first proven through a password-reset link (the details were entered before that). */
    public static final String EMAIL_VERIFIED_BY_RESET = "EMAIL_VERIFIED_BY_RESET";

    public static final String ACTOR_SUPPLIER = "SUPPLIER";
    public static final String ACTOR_ADMIN = "ADMIN";
    public static final String ACTOR_SYSTEM = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID supplierOrgId;
    private String action;
    private String actorType;
    private UUID actorId;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private String ip;
    private Instant createdAt;

    protected SupplierOrgEvent() {}

    public SupplierOrgEvent(UUID supplierOrgId, String action, String actorType, UUID actorId, String fromStatus,
                            String toStatus, String reason, String ip, Instant createdAt) {
        this.supplierOrgId = supplierOrgId;
        this.action = action;
        this.actorType = actorType;
        this.actorId = actorId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.ip = ip;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getSupplierOrgId() { return supplierOrgId; }
    public String getAction() { return action; }
    public String getActorType() { return actorType; }
    public UUID getActorId() { return actorId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public String getIp() { return ip; }
    public Instant getCreatedAt() { return createdAt; }
}
