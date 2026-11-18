package app.stocklist.supplier

import app.stocklist.supplier.core.i18n.AppLanguage
import app.stocklist.supplier.core.i18n.HindiStrings
import app.stocklist.supplier.core.i18n.Lang
import app.stocklist.supplier.core.i18n.asciiDigits
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.core.i18n.trMessage
import app.stocklist.supplier.data.remote.ServerErrors
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text on every screen, every server error and every status must exist in Hindi as well as English. */
class SupplierHindiCatalogueTest {
    @After
    fun backToEnglish() = Lang.setForTest(AppLanguage.ENGLISH)

    private val literal = Regex("""\b(?:tr|trKey|trOne)\(\s*"((?:[^"\\]|\\.)*)"""")
    private val placeholder = Regex("%(\\d+)\\\$s")

    private fun unescape(s: String) = s.replace("\\$", "$").replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")

    private fun dir(vararg candidates: String): File? = candidates.map(::File).firstOrNull { it.isDirectory }

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
        assertTrue("found only ${texts.size} texts", texts.size > 100)
        val missing = texts.filterKeys { it !in HindiStrings.map }
        assertTrue("No Hindi for:\n" + missing.entries.joinToString("\n") { "${it.value}: ${it.key}" }, missing.isEmpty())
    }

    @Test
    fun hindiKeepsTheSameValues() {
        HindiStrings.map.forEach { (en, hi) ->
            assertEquals("placeholders differ for \"$en\"", placeholder.findAll(en).map { it.value }.toSet(),
                placeholder.findAll(hi).map { it.value }.toSet())
            assertTrue("empty Hindi for \"$en\"", hi.isNotBlank())
            assertTrue("not translated: \"$en\"", hi != en || en.none { it.isLetter() })
        }
    }

    /**
     * Every error code the supplier portal defines (SupplierPortalError on the server) has the app's own English and
     * Hindi text, so a server message is never shown untranslated.
     */
    @Test
    fun everyServerErrorCodeHasHindi() {
        val enumFile = listOf(
            "../../backend/src/main/java/app/stocklist/supplieraccount/SupplierPortalError.java",
            "../backend/src/main/java/app/stocklist/supplieraccount/SupplierPortalError.java",
        ).map(::File).firstOrNull { it.isFile } ?: error("backend SupplierPortalError.java not found")
        val codes = Regex("""^\s{4}([A-Z][A-Z_]+)\(HttpStatus\.""", RegexOption.MULTILINE)
            .findAll(enumFile.readText()).map { it.groupValues[1] }.toList()
        assertTrue("found only ${codes.size} codes", codes.size >= 20)
        val missing = codes.filter { it !in ServerErrors.texts }
        assertTrue("No app text for server codes: $missing", missing.isEmpty())
        ServerErrors.texts.values.forEach { assertTrue("No Hindi for \"$it\"", it in HindiStrings.map) }
    }

    /**
     * Every Supplier Network error code (NetworkError on the server) has the app's own English and Hindi text, so a
     * refused accept, dispatch or catalogue change is always explained in the app's languages.
     */
    @Test
    fun everySupplierNetworkErrorCodeHasHindi() {
        val enumFile = listOf(
            "../../backend/src/main/java/app/stocklist/network/NetworkError.java",
            "../backend/src/main/java/app/stocklist/network/NetworkError.java",
        ).map(::File).firstOrNull { it.isFile } ?: error("backend NetworkError.java not found")
        val codes = Regex("""^\s{4}([A-Z][A-Z_]+)\(HttpStatus\.""", RegexOption.MULTILINE)
            .findAll(enumFile.readText()).map { it.groupValues[1] }.toList()
        assertTrue("found only ${codes.size} codes", codes.size >= 25)
        val missing = codes.filter { it !in ServerErrors.texts }
        assertTrue("No app text for Supplier Network codes: $missing", missing.isEmpty())
    }

    @Test
    fun switchingLanguageTranslatesAndKeepsWhatWasTyped() {
        assertEquals("Waiting for approval", tr("Waiting for approval"))
        Lang.setForTest(AppLanguage.HINDI)
        assertEquals("मंज़ूरी का इंतज़ार", tr("Waiting for approval"))
        assertEquals("कारण: GST नंबर गलत है", tr("Reason: %1\$s", "GST नंबर गलत है"))
        assertEquals("आपके बिज़नेस का खाता रोका गया है", ServerErrors.message("SUPPLIER_SUSPENDED", 403))
        // Unknown text (something the supplier typed) is shown as it is.
        assertEquals("Agarwal Traders", trMessage("Agarwal Traders"))
        assertEquals("1234", asciiDigits("१२३४"))
    }

    @Test
    fun bothLanguagesAreShownTogetherInTheApp() {
        Lang.setForTest(AppLanguage.HINDI, showBoth = true)
        val both = tr("Approved")
        assertEquals("मंज़ूर", app.stocklist.supplier.core.i18n.Bilingual.lines(both).first)
        assertEquals("Approved", app.stocklist.supplier.core.i18n.Bilingual.lines(both).second)
    }
}
