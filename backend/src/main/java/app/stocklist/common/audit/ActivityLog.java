package app.stocklist.common.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "activity_log")
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID businessId;
    private UUID userId;
    private String action;
    private String recordType;
    private String recordId;
    private String details;
    private String deviceId;
    private String ip;
    private Instant createdAt;

    protected ActivityLog() {}

    public ActivityLog(UUID businessId, UUID userId, String action, String recordType, String recordId,
                       String details, String deviceId, String ip, Instant createdAt) {
        this.businessId = businessId;
        this.userId = userId;
        this.action = action;
        this.recordType = recordType;
        this.recordId = recordId;
        this.details = details;
        this.deviceId = deviceId;
        this.ip = ip;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getUserId() { return userId; }
    public String getAction() { return action; }
    public String getRecordType() { return recordType; }
    public String getRecordId() { return recordId; }
    public String getDetails() { return details; }
    public String getDeviceId() { return deviceId; }
    public String getIp() { return ip; }
    public Instant getCreatedAt() { return createdAt; }
}
