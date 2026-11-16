package app.stocklist.app.domain.permission

import app.stocklist.app.core.i18n.trKey

/**
 * Mirrors the server catalogue (backend Permission.java / V1__access.sql), same order.
 * A unit test checks the two lists match. The UI only uses this to hide what the server would refuse anyway.
 */
enum class Permission(val label: String, val group: String, val staffDefault: Boolean) {
    SUPPLIER_VIEW(trKey("View suppliers"), trKey("Suppliers"), true),
    SUPPLIER_ADD(trKey("Add suppliers"), trKey("Suppliers"), true),
    SUPPLIER_EDIT(trKey("Edit suppliers"), trKey("Suppliers"), true),
    SUPPLIER_DELETE(trKey("Delete suppliers"), trKey("Suppliers"), false),
    ITEM_VIEW(trKey("View items"), trKey("Items"), true),
    ITEM_ADD(trKey("Add items"), trKey("Items"), true),
    ITEM_EDIT(trKey("Edit items"), trKey("Items"), true),
    ITEM_DELETE(trKey("Delete items"), trKey("Items"), false),
    LIST_CREATE(trKey("Create out-of-stock lists"), trKey("Out-of-stock lists"), true),
    LIST_EDIT(trKey("Edit out-of-stock lists"), trKey("Out-of-stock lists"), true),
    LIST_DELETE(trKey("Delete out-of-stock lists"), trKey("Out-of-stock lists"), false),
    PURCHASE_RECORD(trKey("Record purchases"), trKey("Purchases"), true),
    PURCHASE_EDIT(trKey("Edit purchases"), trKey("Purchases"), false),
    PURCHASE_DELETE(trKey("Delete or cancel purchases"), trKey("Purchases"), false),
    PURCHASE_PRICE_ENTER(trKey("Enter purchase prices"), trKey("Purchases"), true),
    SELLING_PRICE_ENTER(trKey("Enter selling prices"), trKey("Purchases"), true),
    CATALOG_SELLING_PRICE_CHANGE(trKey("Change latest catalogue selling price"), trKey("Purchases"), false),
    PURCHASE_HISTORY_VIEW(trKey("View purchase history"), trKey("Purchases"), true),
    PDF_GENERATE(trKey("Generate PDFs"), trKey("PDFs and expiry"), true),
    PDF_SHARE(trKey("Print and share PDFs"), trKey("PDFs and expiry"), true),
    EXPIRY_VIEW(trKey("View expiry tracking"), trKey("PDFs and expiry"), true),
    EXPECTED_PROFIT_VIEW(trKey("View expected profit"), trKey("Sensitive"), false),
    PROFIT_REPORT_VIEW(trKey("View profit reports"), trKey("Sensitive"), false),
    SETTINGS_MANAGE(trKey("Manage business settings"), trKey("Sensitive"), false),
    STAFF_MANAGE(trKey("Manage staff accounts"), trKey("Sensitive"), false);

    companion object {
        fun fromCodes(codes: Collection<String>): Set<Permission> =
            codes.mapNotNull { code -> entries.firstOrNull { it.name == code } }.toSet()

        val groups: List<String> get() = entries.map { it.group }.distinct()
    }
}
