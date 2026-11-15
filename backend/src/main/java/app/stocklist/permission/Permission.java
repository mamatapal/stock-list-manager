package app.stocklist.permission;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * The fixed permission catalogue. Must match the rows seeded in V1__access.sql
 * and the Kotlin enum in the Android app (checked by tests).
 * The owner implicitly holds all of them; staff hold only what is granted.
 */
public enum Permission {
    SUPPLIER_VIEW(true), SUPPLIER_ADD(true), SUPPLIER_EDIT(true), SUPPLIER_DELETE(false),
    ITEM_VIEW(true), ITEM_ADD(true), ITEM_EDIT(true), ITEM_DELETE(false),
    LIST_CREATE(true), LIST_EDIT(true), LIST_DELETE(false),
    PURCHASE_RECORD(true), PURCHASE_EDIT(false), PURCHASE_DELETE(false),
    PURCHASE_PRICE_ENTER(true), SELLING_PRICE_ENTER(true), CATALOG_SELLING_PRICE_CHANGE(false),
    PURCHASE_HISTORY_VIEW(true),
    PDF_GENERATE(true), PDF_SHARE(true),
    EXPIRY_VIEW(true),
    EXPECTED_PROFIT_VIEW(false), PROFIT_REPORT_VIEW(false),
    SETTINGS_MANAGE(false),
    STAFF_MANAGE(false);

    private final boolean staffDefault;

    Permission(boolean staffDefault) {
        this.staffDefault = staffDefault;
    }

    public boolean staffDefault() {
        return staffDefault;
    }

    public static Set<Permission> all() {
        return EnumSet.allOf(Permission.class);
    }

    public static Set<Permission> staffDefaults() {
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        Arrays.stream(values()).filter(Permission::staffDefault).forEach(set::add);
        return set;
    }
}
