package app.stocklist.supplier.core.i18n

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/** Languages the app's screens can be shown in. What users type (names, notes) can be in any language. */
enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
}

/**
 * The main language chosen on this phone. Screens show both languages: the main one first, the other smaller
 * underneath (see Bilingual.kt). Stored in Compose state, so every screen redraws as soon as it is changed.
 * Until the user picks one, the app follows the phone's language (Hindi phone → Hindi first).
 */
object Lang {
    private const val PREFS = "app_language"
    private const val KEY = "language"

    private val current = mutableStateOf(AppLanguage.ENGLISH)
    private val both = mutableStateOf(false)

    /** The language shown first. */
    val language: AppLanguage get() = current.value

    /** The language shown second (smaller). */
    val other: AppLanguage get() = if (current.value == AppLanguage.HINDI) AppLanguage.ENGLISH else AppLanguage.HINDI

    /** True in the app: every text is shown in both languages. Off before init() (plain unit tests see English only). */
    val showsBoth: Boolean get() = both.value

    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        current.value = AppLanguage.entries.firstOrNull { it.code == saved }
            ?: if (Locale.getDefault().language == "hi") AppLanguage.HINDI else AppLanguage.ENGLISH
        both.value = true
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.code).apply()
        current.value = language
    }

    /** For tests. */
    internal fun setForTest(language: AppLanguage, showBoth: Boolean = false) {
        current.value = language
        both.value = showBoth
    }

    fun translate(english: String, to: AppLanguage = current.value): String =
        if (to == AppLanguage.HINDI) HindiStrings.map[english] ?: english else english
}

/**
 * Text shown to the user, written in English in the code. Returns it in both languages (marked so the screen can show
 * the main language first and the other underneath, even when pieces are joined together).
 * Values go in as %1$s, %2$s… so the Hindi sentence can put them in its own word order; values that are themselves
 * translated text are put in the matching language.
 * A unit test checks that every tr(...) text in the app has a Hindi translation.
 */
fun tr(english: String, vararg args: Any?): String {
    fun inLanguage(language: AppLanguage): String {
        val text = Lang.translate(english, language)
        if (args.isEmpty()) return text
        val values = args.map { if (it is String) Bilingual.pick(it, language) else it }.toTypedArray()
        return String.format(Locale.US, text, *values)
    }
    if (!Lang.showsBoth) return inLanguage(Lang.language)
    return Bilingual.both(inLanguage(Lang.language), inLanguage(Lang.other))
}

/** The main language only. For text that is saved as data (for example a list title), never shown in two languages. */
fun trOne(english: String, vararg args: Any?): String = Bilingual.pick(tr(english, *args), Lang.language)

/** Translates a message that is not written here (a stored sync error, a server reply). Unknown text stays as it is. */
fun trMessage(english: String): String =
    if (!Lang.showsBoth) Lang.translate(english) else Bilingual.both(Lang.translate(english, Lang.language), Lang.translate(english, Lang.other))

/** Marks English text that is stored or sent as it is and translated when shown (trMessage); checked by the catalogue test. */
fun trKey(english: String): String = english
