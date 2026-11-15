package app.stocklist.app

import app.stocklist.app.domain.permission.Permission
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The app's permission list must match the server's catalogue exactly (same codes, same order, same defaults). */
class PermissionContractTest {
    @Test
    fun matchesServerCatalogue() {
        val sql = listOf("../../backend", "../backend").map {
            File(it, "src/main/resources/db/migration/V1__access.sql")
        }.firstOrNull { it.exists() }
        assumeTrue("backend project not found next to the Android project", sql != null)
        val rows = Regex("""\('([A-Z_]+)',\s*'[^']*',\s*(TRUE|FALSE),\s*(\d+)\)""").findAll(sql!!.readText())
            .map { Triple(it.groupValues[1], it.groupValues[2] == "TRUE", it.groupValues[3].toInt()) }
            .sortedBy { it.third }
            .toList()
        assertEquals(Permission.entries.map { it.name }, rows.map { it.first })
        assertEquals(Permission.entries.map { it.staffDefault }, rows.map { it.second })
    }

    @Test
    fun hasTwentyFiveCodes() {
        assertEquals(25, Permission.entries.size)
    }
}
