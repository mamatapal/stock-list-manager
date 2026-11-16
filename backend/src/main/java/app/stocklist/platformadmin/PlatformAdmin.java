package app.stocklist.platformadmin;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** An administrator of the Supplier Administration portal. Created only on the server, never through the API. */
@Entity
@Table(name = "platform_admin")
public class PlatformAdmin {
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";

    @Id
    private UUID id;
    @Version
    private Long version;
    private String username;
    private String fullName;
    private String email;
    private String passwordHash;
    private String totpSecret;
    private long totpLastStep;
    private String status;
    private int failedLoginCount;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected PlatformAdmin() {}

    public PlatformAdmin(String username, String fullName, String email, String passwordHash,
                         String encryptedTotpSecret, Instant now) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.totpSecret = encryptedTotpSecret;
        this.status = ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isActive() { return ACTIVE.equals(status); }
    public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }

    public void usedTotpStep(long step) { totpLastStep = step; }

    public void resetCredentials(String passwordHash, String encryptedTotpSecret, Instant now) {
        this.passwordHash = passwordHash;
        this.totpSecret = encryptedTotpSecret;
        this.totpLastStep = 0;
        this.status = ACTIVE;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    public void deactivate(Instant now) {
        status = INACTIVE;
        updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public boolean recordFailedLogin(int maxFailures, Instant lockUntil) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            failedLoginCount = 0;
            lockedUntil = lockUntil;
            return true;
        }
        return false;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getTotpSecret() { return totpSecret; }
    public long getTotpLastStep() { return totpLastStep; }
    public String getStatus() { return status; }
}
