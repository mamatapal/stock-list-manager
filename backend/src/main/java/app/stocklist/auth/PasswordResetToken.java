package app.stocklist.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {
    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID businessId;
    private UUID userId;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt;

    protected PasswordResetToken() {}

    public PasswordResetToken(UUID businessId, UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.businessId = businessId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) { usedAt = now; }

    public UUID getBusinessId() { return businessId; }
    public UUID getUserId() { return userId; }
}
