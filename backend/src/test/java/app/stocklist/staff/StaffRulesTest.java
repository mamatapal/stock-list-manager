package app.stocklist.staff;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.permission.Permission;
import app.stocklist.user.Role;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffRulesTest {
    private final AuthUser owner = new AuthUser(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER, "owner", "d",
            EnumSet.noneOf(Permission.class));
    private final AuthUser manager = new AuthUser(UUID.randomUUID(), UUID.randomUUID(), Role.STAFF, "mgr", "d",
            EnumSet.of(Permission.STAFF_MANAGE, Permission.ITEM_VIEW, Permission.ITEM_ADD));

    @Test
    void ownerCanGrantAnything() {
        assertDoesNotThrow(() -> StaffService.requireCanChange(owner, Permission.all()));
    }

    @Test
    void staffManagerCannotGrantWhatTheyLack() {
        assertDoesNotThrow(() -> StaffService.requireCanChange(manager, EnumSet.of(Permission.ITEM_ADD)));
        ApiException e = assertThrows(ApiException.class,
                () -> StaffService.requireCanChange(manager, EnumSet.of(Permission.PROFIT_REPORT_VIEW)));
        assertEquals("CANNOT_GRANT", e.code());
    }

    @Test
    void unknownPermissionCodeIsRejected() {
        ApiException e = assertThrows(ApiException.class, () -> StaffService.parse(List.of("ITEM_VIEW", "BOGUS")));
        assertEquals("UNKNOWN_PERMISSION", e.code());
    }
}
