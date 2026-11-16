package app.stocklist.app.core.i18n

/**
 * Text in two languages travels as an ordinary String: each translated piece is wrapped as
 * START main SEPARATOR other END, using invisible Unicode characters. Pieces can be joined with "+", joinToString or
 * buildString like any text; when shown, all main-language parts make the first line and all other-language parts the
 * second line. User data (names, numbers) has no markers and appears in both lines unchanged.
 */
object Bilingual {
    private const val START = '⁡'
    private const val SEPARATOR = '⁢'
    private const val END = '⁣'

    fun both(main: String, other: String): String = if (main == other) main else "$START$main$SEPARATOR$other$END"

    fun isBilingual(text: String): Boolean = text.indexOf(START) >= 0

    /** The text as it reads in one language. */
    fun pick(text: String, language: AppLanguage): String {
        if (!isBilingual(text)) return text
        val wantMain = language == Lang.language
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val start = text.indexOf(START, i)
            if (start < 0) { out.append(text, i, text.length); break }
            out.append(text, i, start)
            val sep = text.indexOf(SEPARATOR, start + 1)
            val end = text.indexOf(END, start + 1)
            if (sep < 0 || end < 0 || sep > end) { out.append(text, start + 1, text.length); break }
            if (wantMain) out.append(text, start + 1, sep) else out.append(text, sep + 1, end)
            i = end + 1
        }
        return out.toString()
    }

    /** First line (main language) and second line (other language), or null when both read the same. */
    fun lines(text: String): Pair<String, String?> {
        if (!isBilingual(text)) return text to null
        val main = pick(text, Lang.language)
        val other = pick(text, Lang.other)
        return if (main == other) main to null else main to other
    }

    /** For places that show plain text on several lines: notifications, PDFs, messages to share. */
    fun twoLines(text: String): String = lines(text).let { (a, b) -> if (b == null) a else "$a\n$b" }

    /** For places with room for one line only: text field labels, chips, email subjects, screen-reader labels. */
    fun oneLine(text: String): String = lines(text).let { (a, b) -> if (b == null) a else "$a / $b" }
}
