package app.stocklist.permission;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * The fixed permission catalogue. Must match the rows seeded in V1__access.sql (the first 25, which the Android
 * app's Kotlin enum mirrors, checked by its tests) and V7__supplier_network.sql (the supplier network permissions,
 * which the app ignores until its Supplier Network screens are added).
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
    STAFF_MANAGE(false),
    // V7: off for staff; owners hold them implicitly.
    SUPPLIER_NETWORK_MANAGE(false),
    PURCHASE_ORDER_MANAGE(false);

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
