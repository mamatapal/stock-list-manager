package app.stocklist.permission;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UserPermissionId implements Serializable {
    private UUID userId;
    private String permissionCode;

    public UserPermissionId() {}

    public UserPermissionId(UUID userId, String permissionCode) {
        this.userId = userId;
        this.permissionCode = permissionCode;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserPermissionId other
                && Objects.equals(userId, other.userId)
                && Objects.equals(permissionCode, other.permissionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, permissionCode);
    }
}
