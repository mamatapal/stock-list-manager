package app.stocklist.app.domain.permission

/**
 * Mirrors the server catalogue (backend Permission.java / V1__access.sql), same order.
 * A unit test checks the two lists match. The UI only uses this to hide what the server would refuse anyway.
 */
enum class Permission(val label: String, val group: String, val staffDefault: Boolean) {
    SUPPLIER_VIEW("View suppliers", "Suppliers", true),
    SUPPLIER_ADD("Add suppliers", "Suppliers", true),
    SUPPLIER_EDIT("Edit suppliers", "Suppliers", true),
    SUPPLIER_DELETE("Delete suppliers", "Suppliers", false),
    ITEM_VIEW("View items", "Items", true),
    ITEM_ADD("Add items", "Items", true),
    ITEM_EDIT("Edit items", "Items", true),
    ITEM_DELETE("Delete items", "Items", false),
    LIST_CREATE("Create out-of-stock lists", "Out-of-stock lists", true),
    LIST_EDIT("Edit out-of-stock lists", "Out-of-stock lists", true),
    LIST_DELETE("Delete out-of-stock lists", "Out-of-stock lists", false),
    PURCHASE_RECORD("Record purchases", "Purchases", true),
    PURCHASE_EDIT("Edit purchases", "Purchases", false),
    PURCHASE_DELETE("Delete or cancel purchases", "Purchases", false),
    PURCHASE_PRICE_ENTER("Enter purchase prices", "Purchases", true),
    SELLING_PRICE_ENTER("Enter selling prices", "Purchases", true),
    CATALOG_SELLING_PRICE_CHANGE("Change latest catalogue selling price", "Purchases", false),
    PURCHASE_HISTORY_VIEW("View purchase history", "Purchases", true),
    PDF_GENERATE("Generate PDFs", "PDFs and expiry", true),
    PDF_SHARE("Print and share PDFs", "PDFs and expiry", true),
    EXPIRY_VIEW("View expiry tracking", "PDFs and expiry", true),
    EXPECTED_PROFIT_VIEW("View expected profit", "Sensitive", false),
    PROFIT_REPORT_VIEW("View profit reports", "Sensitive", false),
    SETTINGS_MANAGE("Manage business settings", "Sensitive", false),
    STAFF_MANAGE("Manage staff accounts", "Sensitive", false);

    companion object {
        fun fromCodes(codes: Collection<String>): Set<Permission> =
            codes.mapNotNull { code -> entries.firstOrNull { it.name == code } }.toSet()

        val groups: List<String> get() = entries.map { it.group }.distinct()
    }
}
