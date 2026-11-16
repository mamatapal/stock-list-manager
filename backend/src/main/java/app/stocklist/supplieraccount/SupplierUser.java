package app.stocklist.supplieraccount;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Someone who signs in to the Supplier Management app. The lower-case email is the sign-in name. */
@Entity
@Table(name = "supplier_user")
public class SupplierUser {
    public static final String OWNER = "SUPPLIER_OWNER";
    public static final String ACTIVE = "ACTIVE";

    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID supplierOrgId;
    private String email;
    private String fullName;
    private String mobile;
    private String passwordHash;
    private String role;
    private String status;
    private Instant emailVerifiedAt;
    private int failedLoginCount;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private int tokenVersion;
    private Instant createdAt;
    private Instant updatedAt;

    protected SupplierUser() {}

    public SupplierUser(UUID supplierOrgId, String email, String fullName, String mobile, String passwordHash,
                        Instant now) {
        this.id = UUID.randomUUID();
        this.supplierOrgId = supplierOrgId;
        this.email = email;
        this.fullName = fullName;
        this.mobile = mobile;
        this.passwordHash = passwordHash;
        this.role = OWNER;
        this.status = ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isActive() { return ACTIVE.equals(status); }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }

    public void emailVerified(Instant now) {
        if (emailVerifiedAt == null) emailVerifiedAt = now;
        updatedAt = now;
    }

    public void updateContact(String fullName, String mobile, Instant now) {
        this.fullName = fullName;
        this.mobile = mobile;
        this.updatedAt = now;
    }

    /** New password: every access token already issued stops working on its next use. */
    public void changePassword(String newHash, Instant now) {
        passwordHash = newHash;
        tokenVersion++;
        failedLoginCount = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** Returns true when this failure locks the account. */
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
    public Long getVersion() { return version; }
    public UUID getSupplierOrgId() { return supplierOrgId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getMobile() { return mobile; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public int getTokenVersion() { return tokenVersion; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
