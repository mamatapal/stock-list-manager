package app.stocklist.common.security;

import app.stocklist.permission.Permission;
import app.stocklist.user.Role;
import java.util.Set;
import java.util.UUID;

/** The verified caller of the current request. businessId comes only from the signed token. */
public record AuthUser(UUID userId, UUID businessId, Role role, String username, String deviceId,
                       Set<Permission> permissions) {

    public boolean isOwner() {
        return role == Role.OWNER;
    }

    public boolean has(Permission p) {
        return isOwner() || permissions.contains(p);
    }

    /** What the caller effectively holds: everything for the owner. */
    public Set<Permission> effectivePermissions() {
        return isOwner() ? Permission.all() : permissions;
    }
}
