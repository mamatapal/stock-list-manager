package app.stocklist.app.core.i18n

/**
 * Names the user creates (items, suppliers, categories, units) are saved with both an English and a Hindi spelling in
 * the one name field: "Sugar / शुगर". English always comes first, so lists stay in A–Z order, searches find either
 * spelling, and older app versions still show a readable name. A name with only one spelling is saved as it is.
 */
object BilingualName {
    const val SEPARATOR = " / "

    fun hasDevanagari(text: String) = text.any { it in 'ऀ'..'ॿ' }

    private fun hasLatin(text: String) = text.any { it in 'a'..'z' || it in 'A'..'Z' }

    /** (English, Hindi) as typed, without trimming, so a space typed at the end of a word is kept while typing. */
    fun split(name: String): Pair<String, String> {
        val at = name.indexOf(SEPARATOR)
        if (at >= 0) {
            val left = name.substring(0, at)
            val right = name.substring(at + SEPARATOR.length)
            if (right.isNotBlank() && left.isNotBlank() && !hasDevanagari(left) && hasDevanagari(right) && !hasLatin(right)) return left to right
        }
        return if (hasDevanagari(name) && !hasLatin(name)) "" to name else name to ""
    }

    /** English and Hindi in one name. Nothing is trimmed here (see [clean]). */
    fun combine(english: String, hindi: String): String = when {
        hindi.isBlank() -> english
        english.isBlank() -> hindi
        else -> english + SEPARATOR + hindi
    }

    /** The name ready to save: spaces tidied in each spelling. */
    fun clean(name: String): String {
        val (en, hi) = split(name)
        fun tidy(s: String) = s.trim().replace(Regex("\\s+"), " ")
        return combine(tidy(en), tidy(hi))
    }

    /** English spelling used to find duplicates; a Hindi-only name is its own key. */
    fun key(name: String): String = split(name).let { (en, hi) -> en.trim().ifEmpty { hi.trim() } }

    /** Both spellings when the name has two, otherwise null. */
    fun both(name: String): Pair<String, String>? {
        val (en, hi) = split(name)
        return if (en.isNotBlank() && hi.isNotBlank()) en.trim() to hi.trim() else null
    }
}
