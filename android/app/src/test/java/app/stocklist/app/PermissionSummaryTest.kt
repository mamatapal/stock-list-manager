package app.stocklist.app

import app.stocklist.app.domain.permission.Permission
import app.stocklist.app.domain.permission.PermissionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionSummaryTest {
    @Test
    fun groupsOnlyHeldPermissionsInCatalogueOrder() {
        val grouped = PermissionSummary.grouped(setOf(Permission.ITEM_VIEW, Permission.SUPPLIER_VIEW, Permission.EXPIRY_VIEW))
        assertEquals(listOf("Suppliers", "Items", "PDFs and expiry"), grouped.map { it.first })
        assertEquals(listOf("View items"), grouped[1].second)
    }

    @Test
    fun noPermissionsMeansNoGroups() {
        assertEquals(emptyList<Pair<String, List<String>>>(), PermissionSummary.grouped(emptySet()))
    }

    @Test
    fun auditActionsAreReadable() {
        assertEquals("Deactivated a staff account", PermissionSummary.actionLabel("STAFF_DEACTIVATED"))
        assertEquals("Something new", PermissionSummary.actionLabel("SOMETHING_NEW"))
    }
}
