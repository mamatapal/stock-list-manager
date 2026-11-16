package app.stocklist.platformadmin;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A signed-in portal browser. The browser has the random id in an HttpOnly cookie; only its SHA-256 is stored. */
@Entity
@Table(name = "platform_admin_session")
public class AdminSession {
    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID adminId;
    private String sessionHash;
    private Instant createdAt;
    private Instant lastSeenAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private String ip;

    protected AdminSession() {}

    public AdminSession(UUID adminId, String sessionHash, Instant now, Instant expiresAt, String ip) {
        this.id = UUID.randomUUID();
        this.adminId = adminId;
        this.sessionHash = sessionHash;
        this.createdAt = now;
        this.lastSeenAt = now;
        this.expiresAt = expiresAt;
        this.ip = ip;
    }

    public boolean isUsable(Instant now, java.time.Duration idle) {
        return revokedAt == null && expiresAt.isAfter(now) && lastSeenAt.plus(idle).isAfter(now);
    }

    public void seen(Instant now) { lastSeenAt = now; }

    public void revoke(Instant now) {
        if (revokedAt == null) revokedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getAdminId() { return adminId; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
