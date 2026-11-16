package app.stocklist.supplieraccount;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Same design as the shop app's refresh tokens: stored hashed, rotated on every use, families revoked together. */
@Entity
@Table(name = "supplier_refresh_token")
public class SupplierRefreshToken {
    public static final String REASON_ROTATED = "ROTATED";
    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_REUSE = "REUSE_DETECTED";
    public static final String REASON_PASSWORD = "PASSWORD_CHANGED";
    public static final String REASON_INACTIVE = "ACCOUNT_INACTIVE";

    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID userId;
    private UUID familyId;
    private String tokenHash;
    private String deviceId;
    private String deviceName;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private String revokeReason;
    private UUID replacedBy;

    protected SupplierRefreshToken() {}

    public SupplierRefreshToken(UUID userId, UUID familyId, String tokenHash, String deviceId, String deviceName,
                                Instant issuedAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }

    public void rotatedTo(UUID next, Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            revokeReason = REASON_ROTATED;
        }
        replacedBy = next;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getRevokeReason() { return revokeReason; }
}
