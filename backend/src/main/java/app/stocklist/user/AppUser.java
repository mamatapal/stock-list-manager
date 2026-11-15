package app.stocklist.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID businessId;
    private String username;
    private String fullName;
    private String mobile;
    private String email;
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    private Role role;
    @Enumerated(EnumType.STRING)
    private UserStatus status;
    private boolean mustChangePassword;
    private int failedLoginCount;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private int tokenVersion;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    protected AppUser() {}

    public AppUser(UUID businessId, String username, String fullName, String mobile, String email,
                   String passwordHash, Role role, boolean mustChangePassword, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.businessId = businessId;
        this.username = username;
        this.fullName = fullName;
        this.mobile = mobile;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.mustChangePassword = mustChangePassword;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isOwner() { return role == Role.OWNER; }
    public boolean isActive() { return status == UserStatus.ACTIVE; }
    public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }

    /** Invalidates every access token already issued to this user on the next request. */
    public void bumpTokenVersion(Instant now) {
        tokenVersion++;
        updatedAt = now;
    }

    public void changePassword(String newHash, boolean mustChange, Instant now) {
        this.passwordHash = newHash;
        this.mustChangePassword = mustChange;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        bumpTokenVersion(now);
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

    public void setStatus(UserStatus status, Instant now) {
        this.status = status;
        bumpTokenVersion(now);
    }

    public void updateDetails(String fullName, String mobile, String email, Instant now) {
        this.fullName = fullName;
        this.mobile = mobile;
        this.email = email;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public int getTokenVersion() { return tokenVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
