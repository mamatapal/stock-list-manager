package app.stocklist.common.security;

import app.stocklist.permission.Permission;
import org.springframework.stereotype.Component;

/** Used from @PreAuthorize, e.g. @PreAuthorize("@perm.has('STAFF_MANAGE')"). */
@Component("perm")
public class PermissionChecker {

    public boolean has(String code) {
        return CurrentUser.get().has(Permission.valueOf(code));
    }

    public boolean owner() {
        return CurrentUser.get().isOwner();
    }
}
