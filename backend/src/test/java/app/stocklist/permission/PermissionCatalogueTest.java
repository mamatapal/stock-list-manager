package app.stocklist.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionCatalogueTest {
    @Test
    void hasOneCodePerSpecItem() {
        assertEquals(25, Permission.values().length);
    }

    @Test
    void staffDefaultsExcludeSensitiveOwnerFunctions() {
        Set<Permission> d = Permission.staffDefaults();
        for (Permission sensitive : Set.of(Permission.STAFF_MANAGE, Permission.SETTINGS_MANAGE,
                Permission.PROFIT_REPORT_VIEW, Permission.EXPECTED_PROFIT_VIEW,
                Permission.CATALOG_SELLING_PRICE_CHANGE, Permission.PURCHASE_DELETE)) {
            assertFalse(d.contains(sensitive), sensitive + " must be off by default");
        }
        for (Permission normal : Set.of(Permission.PURCHASE_RECORD, Permission.SUPPLIER_VIEW, Permission.ITEM_ADD,
                Permission.LIST_CREATE, Permission.PDF_SHARE, Permission.EXPIRY_VIEW)) {
            assertTrue(d.contains(normal), normal + " must be on by default for normal purchasing work");
        }
    }
}
