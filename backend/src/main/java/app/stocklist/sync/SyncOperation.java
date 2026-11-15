package app.stocklist.sync;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Ledger row: the outcome of one pushed operation, keyed by the phone's op id. */
@Entity
@Table(name = "sync_operation")
public class SyncOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID businessId;
    private UUID opId;
    private UUID userId;
    private String deviceId;
    private String entityType;
    private UUID entityId;
    private String action;
    private String resultStatus;
    private String resultCode;
    private String resultMessage;
    private Long resultVersion;
    private Instant createdAt;

    protected SyncOperation() {}

    public SyncOperation(UUID businessId, UUID opId, UUID userId, String deviceId, String entityType, UUID entityId,
                         String action, String status, String code, String message, Long version, Instant now) {
        this.businessId = businessId;
        this.opId = opId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.resultStatus = status;
        this.resultCode = code;
        this.resultMessage = message == null ? null : message.length() > 300 ? message.substring(0, 300) : message;
        this.resultVersion = version;
        this.createdAt = now;
    }

    public UUID getOpId() { return opId; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getResultStatus() { return resultStatus; }
    public String getResultCode() { return resultCode; }
    public String getResultMessage() { return resultMessage; }
    public Long getResultVersion() { return resultVersion; }
}
