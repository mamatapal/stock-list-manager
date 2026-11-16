package app.stocklist.app

import app.stocklist.app.core.i18n.AppLanguage
import app.stocklist.app.core.i18n.HindiStrings
import app.stocklist.app.core.i18n.Lang
import app.stocklist.app.core.i18n.asciiDigits
import app.stocklist.app.core.i18n.tr
import app.stocklist.app.core.i18n.trMessage
import app.stocklist.app.core.pdf.PdfFormat
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.catalogue.Decimals
import app.stocklist.app.domain.catalogue.ExpiryCalculator
import app.stocklist.app.domain.permission.Permission
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text on screen must have Hindi, and switching languages must not change what users typed. */
class HindiCatalogueTest {
    @After
    fun backToEnglish() = Lang.setForTest(AppLanguage.ENGLISH)

    private val literal = Regex("""\b(?:tr|trKey)\(\s*"((?:[^"\\]|\\.)*)"""")
    private val placeholder = Regex("%(\\d+)\\\$s") // matches %1$s

    private fun unescape(s: String) = s.replace("\\$", "$").replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")

    private fun dir(vararg candidates: String): File? = candidates.map(::File).firstOrNull { it.isDirectory }

    /** Every tr("…") and trKey("…") text written in the app's code. */
    private fun appTexts(): Map<String, String> {
        val src = dir("src/main/java", "app/src/main/java") ?: error("app sources not found")
        val found = linkedMapOf<String, String>()
        src.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            literal.findAll(f.readText()).forEach { found.putIfAbsent(unescape(it.groupValues[1]), f.name) }
        }
        return found
    }

    @Test
    fun everyTextOnScreenHasHindi() {
        val texts = appTexts()
        assertTrue("found only ${texts.size} texts", texts.size > 700)
        val missing = texts.filterKeys { it !in HindiStrings.map }
        assertTrue("No Hindi for:\n" + missing.entries.joinToString("\n") { "${it.value}: ${it.key}" }, missing.isEmpty())
    }

    @Test
    fun hindiKeepsTheSameValues() {
        HindiStrings.map.forEach { (en, hi) ->
            assertEquals("placeholders differ for \"$en\"", placeholder.findAll(en).map { it.value }.toSet(),
                placeholder.findAll(hi).map { it.value }.toSet())
            assertEquals("line breaks differ for \"$en\"", en.count { it == '\n' }, hi.count { it == '\n' })
            assertTrue("empty Hindi for \"$en\"", hi.isNotBlank())
        }
    }

    /** The server's fixed messages reach the screen as error text; they are translated by exact match. */
    @Test
    fun serverMessagesHaveHindi() {
        val backend = dir("../../backend/src/main/java", "../backend/src/main/java") ?: return
        val codeThenText = Regex(""""[A-Z][A-Z_]{2,}"\s*,\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)""")
        val annotation = Regex("""message\s*=\s*"([^"]+)"""")
        val messages = mutableSetOf<String>()
        backend.walkTopDown().filter { it.extension == "java" }.forEach { f ->
            val s = f.readText()
            codeThenText.findAll(s).forEach { m ->
                messages += Regex(""""((?:[^"\\]|\\.)*)"""").findAll(m.groupValues[1]).joinToString("") { it.groupValues[1] }
            }
            annotation.findAll(s).forEach { messages += it.groupValues[1] }
        }
        // Texts built from parts on the server, or only seen by developers (never shown for normal app use).
        val notShown = setOf("An item called \\\"", "Unknown permission: ", "You do not have permission to ",
            "DRAFT or SAVED", "OWNER or STAFF", "PER_UNIT or TOTAL", "RECEIPT or CORRECTION", "At most 50 operations per push",
            "entityId must be the delivery id, and purchaseId is required",
            "expiry must be EXPIRED, WITHIN_7, WITHIN_30, WITHIN_90 or ALL",
            "opId, entityType, entityId and action (UPSERT or DELETE) are required")
        val missing = messages.filter { it.contains(' ') && it !in notShown && it !in HindiStrings.map }
        assertTrue("Server messages without Hindi:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun switchingLanguageTranslatesAndFillsInValues() {
        assertEquals("Expires in 5 days", tr("Expires in %1\$s days", 5))
        Lang.setForTest(AppLanguage.HINDI)
        assertEquals("5 दिनों में एक्सपायर होगा", tr("Expires in %1\$s days", 5))
        assertEquals("सेव करें", tr("Save"))
        assertEquals("मौजूदा पासवर्ड गलत है", trMessage("Current password is incorrect"))
        // Unknown text (a name the user typed, a new server message) is shown as it is.
        assertEquals("रमेश किराना", trMessage("रमेश किराना"))
        assertEquals("Ramesh Kirana", trMessage("Ramesh Kirana"))
        Lang.setForTest(AppLanguage.ENGLISH)
        assertEquals("Save", tr("Save"))
    }

    @Test
    fun namesThatAreShownTranslatedHaveHindi() {
        Lang.setForTest(AppLanguage.HINDI)
        Permission.entries.forEach {
            assertTrue(it.label, trMessage(it.label) != it.label)
            assertTrue(it.group, trMessage(it.group) != it.group)
        }
        ExpiryCalculator.Filter.entries.forEach { assertTrue(it.label, trMessage(it.label) != it.label) }
        BuiltInUnits.all.forEach { assertTrue(it.name, it.displayName != it.name) }
        // Codes and the names stored or sent to the server stay English.
        assertEquals("Pieces", BuiltInUnits.all.first().name)
        assertEquals("View suppliers", Permission.SUPPLIER_VIEW.label)
        assertEquals("1 दिन में एक्सपायर होगा", ExpiryCalculator.label(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun hindiDigitsAreReadAsNumbers() {
        assertEquals("12.50", asciiDigits("१२.५०"))
        assertEquals("9876543210", asciiDigits("९८७६५४३२१०"))
        assertEquals("दूध 2 लीटर", asciiDigits("दूध २ लीटर"))
        assertEquals("abc 123", asciiDigits("abc 123"))
        assertEquals(BigDecimal("12.5"), Decimals.parse("१२.५", "Quantity", 3, false))
    }

    @Test
    fun hindiNamesStayInPdfFileNames() {
        assertEquals("खरीद_रमेश-किराना_2026-11-16.pdf", PdfFormat.fileName("खरीद", "रमेश किराना", "2026-11-16"))
        assertEquals("List_Monday-s-order.pdf", PdfFormat.fileName("List", "Monday's order ", null))
    }
}
