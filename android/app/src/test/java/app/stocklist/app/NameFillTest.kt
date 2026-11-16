package app.stocklist.app

import app.stocklist.app.core.i18n.AppLanguage
import app.stocklist.app.core.i18n.Bilingual
import app.stocklist.app.core.i18n.BilingualName
import app.stocklist.app.core.i18n.Lang
import app.stocklist.app.core.i18n.Transliterate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Names are saved in English and Hindi together; the Hindi is the English written by sound, not translated. */
class NameFillTest {
    @After fun reset() = Lang.setForTest(AppLanguage.ENGLISH, false)

    @Test fun commonShopWordsGetTheirUsualHindiSpelling() {
        assertEquals("शुगर", Transliterate.toHindi("Sugar"))
        assertEquals("बासमती राइस", Transliterate.toHindi("Basmati Rice"))
        assertEquals("5 केजी शुगर", Transliterate.toHindi("5 kg sugar"))
        assertEquals("कप्स", Transliterate.toHindi("Cups"))
    }

    @Test fun otherWordsAreWrittenBySoundAndNeverLeaveLatinLetters() {
        assertEquals("कमल", Transliterate.toHindi("kamal"))
        assertEquals("सिल्क", Transliterate.toHindi("silk"))
        for (w in listOf("Parle-G Biscuit", "Zebra", "Quick Fix", "Xerox", "Lux Soap 100g", "Tata Salt", "Aashirvaad Atta")) {
            val hi = Transliterate.toHindi(w)
            assertTrue("$w -> $hi", hi.none { it in 'a'..'z' || it in 'A'..'Z' })
            assertTrue("$w -> $hi", hi.isNotBlank())
        }
    }

    @Test fun spacesDigitsAndPunctuationStayAsTyped() {
        assertEquals("5 केजी ", Transliterate.toHindi("5 kg "))
        assertEquals("", Transliterate.toHindi(""))
        assertEquals("100 – 200", Transliterate.toHindi("100 – 200"))
    }

    @Test fun hindiBecomesEnglishLetters() {
        assertEquals("Sugar", Transliterate.toEnglish("शुगर"))
        assertEquals("Kamal", Transliterate.toEnglish("कमल"))
        assertTrue(Transliterate.toEnglish("आलू प्याज़").none { it in 'ऀ'..'ॿ' })
        assertEquals("Chips 5", Transliterate.toEnglish("चिप्स 5"))
    }

    @Test fun namesCombineSplitAndClean() {
        assertEquals("Sugar / शुगर", BilingualName.combine("Sugar", "शुगर"))
        assertEquals("Sugar", BilingualName.combine("Sugar", ""))
        assertEquals("शुगर", BilingualName.combine("", "शुगर"))
        assertEquals("Sugar" to "शुगर", BilingualName.split("Sugar / शुगर"))
        assertEquals("Salt / Pepper" to "", BilingualName.split("Salt / Pepper"))
        assertEquals("" to "चीनी", BilingualName.split("चीनी"))
        assertEquals("Basmati " to "बासमती ", BilingualName.split(BilingualName.combine("Basmati ", "बासमती ")))
        assertEquals("Sugar / शुगर", BilingualName.clean("  Sugar   /  शुगर "))
        assertEquals("Basmati Rice / बासमती राइस", BilingualName.clean("Basmati   Rice  /  बासमती   राइस"))
        assertEquals("Sugar", BilingualName.key("Sugar / शुगर"))
        assertEquals("चीनी", BilingualName.key("चीनी"))
        assertNull(BilingualName.both("Sugar"))
    }

    @Test fun savedNamesShowInTwoLinesWithTheMainLanguageFirst() {
        Lang.setForTest(AppLanguage.ENGLISH, true)
        assertEquals("Sugar" to "शुगर", Bilingual.lines("Sugar / शुगर"))
        assertEquals("Sugar\nशुगर", Bilingual.twoLines("Sugar / शुगर"))
        Lang.setForTest(AppLanguage.HINDI, true)
        assertEquals("शुगर" to "Sugar", Bilingual.lines("Sugar / शुगर"))
        assertEquals("शुगर / Sugar", Bilingual.oneLine("Sugar / शुगर"))
        assertEquals("शुगर", Bilingual.pick("Sugar / शुगर", AppLanguage.HINDI))
        assertEquals("Sugar", Bilingual.pick("Sugar / शुगर", AppLanguage.ENGLISH))
    }

    @Test fun plainTextIsLeftAlone() {
        Lang.setForTest(AppLanguage.ENGLISH, true)
        assertEquals("Rice" to null, Bilingual.lines("Rice"))
        assertEquals("Salt / Pepper" to null, Bilingual.lines("Salt / Pepper"))
        assertFalse(Bilingual.isBilingual("Sugar / शुगर"))
    }
}
