package app.stocklist.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A phone a user has signed in on. */
@Entity
@Table(name = "device")
public class Device {
    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID businessId;
    private UUID userId;
    private String deviceId;
    private String name;
    private String appVersion;
    private String fcmToken;
    private Instant lastSeenAt;
    private Instant createdAt;

    protected Device() {}

    public Device(UUID businessId, UUID userId, String deviceId, String name, String appVersion, Instant now) {
        this.id = UUID.randomUUID();
        this.businessId = businessId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.name = name;
        this.appVersion = appVersion;
        this.lastSeenAt = now;
        this.createdAt = now;
    }

    public void seen(String name, String appVersion, Instant now) {
        if (name != null) this.name = name;
        if (appVersion != null) this.appVersion = appVersion;
        this.lastSeenAt = now;
    }

    public String getDeviceId() { return deviceId; }
    public String getName() { return name; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
