package app.stocklist.supplieraccount;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A single-use emailed link. Only the SHA-256 of the random token is stored. */
@Entity
@Table(name = "supplier_email_token")
public class SupplierEmailToken {
    public static final String VERIFY_EMAIL = "VERIFY_EMAIL";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID userId;
    private String purpose;
    private String tokenHash;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant usedAt;

    protected SupplierEmailToken() {}

    public SupplierEmailToken(UUID userId, String purpose, String tokenHash, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() { return usedAt != null; }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }

    public void markUsed(Instant now) {
        if (usedAt == null) usedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getPurpose() { return purpose; }
    public Instant getCreatedAt() { return createdAt; }
}
