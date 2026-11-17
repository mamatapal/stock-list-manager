package app.stocklist.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PermissionCatalogueTest {
    @Test
    void hasOneCodePerSpecItem() {
        // 25 from V1 and the two supplier network permissions from V7.
        assertEquals(27, Permission.values().length);
    }

    @Test
    void staffDefaultsExcludeSensitiveOwnerFunctions() {
        Set<Permission> d = Permission.staffDefaults();
        for (Permission sensitive : Set.of(Permission.STAFF_MANAGE, Permission.SETTINGS_MANAGE,
                Permission.PROFIT_REPORT_VIEW, Permission.EXPECTED_PROFIT_VIEW,
                Permission.CATALOG_SELLING_PRICE_CHANGE, Permission.PURCHASE_DELETE,
                Permission.SUPPLIER_NETWORK_MANAGE, Permission.PURCHASE_ORDER_MANAGE)) {
            assertFalse(d.contains(sensitive), sensitive + " must be off by default");
        }
        for (Permission normal : Set.of(Permission.PURCHASE_RECORD, Permission.SUPPLIER_VIEW, Permission.ITEM_ADD,
                Permission.LIST_CREATE, Permission.PDF_SHARE, Permission.EXPIRY_VIEW)) {
            assertTrue(d.contains(normal), normal + " must be on by default for normal purchasing work");
        }
    }

    private static final List<String> V1_CODES = List.of(
            "SUPPLIER_VIEW", "SUPPLIER_ADD", "SUPPLIER_EDIT", "SUPPLIER_DELETE",
            "ITEM_VIEW", "ITEM_ADD", "ITEM_EDIT", "ITEM_DELETE",
            "LIST_CREATE", "LIST_EDIT", "LIST_DELETE",
            "PURCHASE_RECORD", "PURCHASE_EDIT", "PURCHASE_DELETE",
            "PURCHASE_PRICE_ENTER", "SELLING_PRICE_ENTER", "CATALOG_SELLING_PRICE_CHANGE",
            "PURCHASE_HISTORY_VIEW", "PDF_GENERATE", "PDF_SHARE", "EXPIRY_VIEW",
            "EXPECTED_PROFIT_VIEW", "PROFIT_REPORT_VIEW", "SETTINGS_MANAGE", "STAFF_MANAGE");

    private static Set<String> names(Set<Permission> set) {
        Set<String> out = new TreeSet<>();
        set.forEach(p -> out.add(p.name()));
        return out;
    }

    @Test
    void theFirstReleaseSetIsExactlyTheOriginal25Codes() {
        assertEquals(25, Permission.FIRST_RELEASE.size());
        assertEquals(new TreeSet<>(V1_CODES), names(Permission.FIRST_RELEASE));
        assertFalse(Permission.FIRST_RELEASE.contains(Permission.SUPPLIER_NETWORK_MANAGE));
        assertFalse(Permission.FIRST_RELEASE.contains(Permission.PURCHASE_ORDER_MANAGE));
    }

    @Test
    void theFirstReleaseSetMatchesThePermissionsSeededByV1() throws IOException {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V1__access.sql")) {
            assertTrue(in != null, "V1__access.sql on the classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = sql.indexOf("INSERT INTO permission");
        assertTrue(start >= 0, "V1 seeds the permission table");
        String insert = sql.substring(start, sql.indexOf(';', start));
        Set<String> seeded = new TreeSet<>();
        Matcher m = Pattern.compile("\\('([A-Z_]+)'").matcher(insert);
        while (m.find()) seeded.add(m.group(1));
        assertEquals(25, seeded.size(), insert);
        assertEquals(seeded, names(Permission.FIRST_RELEASE));
    }

    @Test
    void permissionsAddedAfterTheFirstReleaseAreNeverInIt() {
        // Every code beyond the original 25 (today the two Supplier Network codes, and any added later) is outside
        // the set older apps are assumed to understand.
        for (Permission p : Permission.values()) {
            assertEquals(V1_CODES.contains(p.name()), Permission.FIRST_RELEASE.contains(p), p.name());
        }
    }
}
