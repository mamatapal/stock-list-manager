package app.stocklist.permission;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "user_permission")
@IdClass(UserPermissionId.class)
public class UserPermission implements Persistable<UserPermissionId> {
    @Id
    private UUID userId;
    @Id
    private String permissionCode;
    private UUID businessId;
    private UUID grantedBy;
    private Instant grantedAt;
    @Transient
    private boolean fresh = true;

    protected UserPermission() {}

    public UserPermission(UUID businessId, UUID userId, Permission permission, UUID grantedBy, Instant grantedAt) {
        this.businessId = businessId;
        this.userId = userId;
        this.permissionCode = permission.name();
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
    }

    public Permission permission() {
        return Permission.valueOf(permissionCode);
    }

    public UUID getBusinessId() { return businessId; }
    public UUID getUserId() { return userId; }

    @Override
    public UserPermissionId getId() {
        return new UserPermissionId(userId, permissionCode);
    }

    /** Rows are only ever inserted or deleted, never updated. */
    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        fresh = false;
    }
}
