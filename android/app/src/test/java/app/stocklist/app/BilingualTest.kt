package app.stocklist.app

import app.stocklist.app.core.i18n.AppLanguage
import app.stocklist.app.core.i18n.Bilingual
import app.stocklist.app.core.i18n.Lang
import app.stocklist.app.core.i18n.tr
import app.stocklist.app.core.i18n.trMessage
import app.stocklist.app.core.i18n.trOne
import app.stocklist.app.core.pdf.PdfFormat
import app.stocklist.app.domain.catalogue.ExpiryCalculator
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The app shows every text in both languages: the chosen one first, the other underneath. */
class BilingualTest {
    @After
    fun reset() = Lang.setForTest(AppLanguage.ENGLISH, showBoth = false)

    @Test
    fun hindiFirstThenEnglish() {
        Lang.setForTest(AppLanguage.HINDI, showBoth = true)
        assertEquals("सेव करें" to "Save", Bilingual.lines(tr("Save")))
        assertEquals("सेव करें\nSave", Bilingual.twoLines(tr("Save")))
        assertEquals("सेव करें / Save", Bilingual.oneLine(tr("Save")))
    }

    @Test
    fun englishFirstThenHindi() {
        Lang.setForTest(AppLanguage.ENGLISH, showBoth = true)
        assertEquals("Save" to "सेव करें", Bilingual.lines(tr("Save")))
        assertEquals("5 days" to null, Bilingual.lines("5 days")) // text with no translation shows once
    }

    @Test
    fun valuesAndJoinedPiecesStayInTheirOwnLine() {
        Lang.setForTest(AppLanguage.HINDI, showBoth = true)
        // A value goes into both lines unchanged.
        assertEquals("5 दिनों में एक्सपायर होगा" to "Expires in 5 days", Bilingual.lines(tr("Expires in %1\$s days", 5)))
        // Pieces joined with + : every Hindi piece in line 1, every English piece in line 2.
        val joined = tr("Billed %1\$s %2\$s", "2", "KG") + tr(" · no purchase price")
        assertEquals("बिल में 2 KG · कोई खरीद मूल्य नहीं" to "Billed 2 KG · no purchase price", Bilingual.lines(joined))
        // Translated text used as a value inside another translated text.
        val nested = tr("%1\$s will be removed from this phone and will not be sent. ", tr("Item"))
        assertEquals("आइटम इस फ़ोन से हट जाएगा और भेजा नहीं जाएगा। " to "Item will be removed from this phone and will not be sent. ",
            Bilingual.lines(nested))
        // Lists joined with joinToString.
        val list = listOf(tr("batch %1\$s", "B7"), "2 KG").joinToString(" · ")
        assertEquals("बैच B7 · 2 KG" to "batch B7 · 2 KG", Bilingual.lines(list))
        // Names typed by users stay as typed in both lines.
        assertEquals("रमेश किराना · बिल में 1 KG" to "रमेश किराना · Billed 1 KG",
            Bilingual.lines("रमेश किराना · " + tr("Billed %1\$s %2\$s", "1", "KG")))
    }

    @Test
    fun messagesAndLabelsShowBoth() {
        Lang.setForTest(AppLanguage.HINDI, showBoth = true)
        assertEquals("मौजूदा पासवर्ड गलत है" to "Current password is incorrect", Bilingual.lines(trMessage("Current password is incorrect")))
        assertEquals("1 दिन में एक्सपायर होगा" to "Expires in 1 day",
            Bilingual.lines(ExpiryCalculator.label(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1))))
        assertNull(Bilingual.lines(trMessage("A message the app does not know")).second)
    }

    @Test
    fun savedTextAndFileNamesUseOneLanguage() {
        Lang.setForTest(AppLanguage.HINDI, showBoth = true)
        val title = trOne("Copy of %1\$s", "सोमवार की लिस्ट")
        assertEquals("सोमवार की लिस्ट की कॉपी", title)
        assertFalse(Bilingual.isBilingual(title))
        assertEquals("खरीद_K-1_2026-11-16.pdf", PdfFormat.fileName(tr("Purchase"), "K-1", "2026-11-16"))
        Lang.setForTest(AppLanguage.ENGLISH, showBoth = true)
        assertEquals("Purchase_K-1_2026-11-16.pdf", PdfFormat.fileName(tr("Purchase"), "K-1", "2026-11-16"))
    }
}
