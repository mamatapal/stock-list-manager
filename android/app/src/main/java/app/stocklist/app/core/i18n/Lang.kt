package app.stocklist.app.core.i18n

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/** Languages the app's screens can be shown in. What users type (names, notes) can be in any language. */
enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
}

/**
 * The language chosen on this phone. Stored in Compose state, so every screen redraws in the new language as soon
 * as it is changed. Until the user picks one, the app follows the phone's language (Hindi phone → Hindi app).
 */
object Lang {
    private const val PREFS = "app_language"
    private const val KEY = "language"

    private val current = mutableStateOf(AppLanguage.ENGLISH)

    val language: AppLanguage get() = current.value

    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        current.value = AppLanguage.entries.firstOrNull { it.code == saved }
            ?: if (Locale.getDefault().language == "hi") AppLanguage.HINDI else AppLanguage.ENGLISH
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.code).apply()
        current.value = language
    }

    /** For tests. */
    internal fun setForTest(language: AppLanguage) {
        current.value = language
    }

    fun translate(english: String): String =
        if (current.value == AppLanguage.HINDI) HindiStrings.map[english] ?: english else english
}

/**
 * Text shown to the user, written in English in the code and translated when Hindi is chosen.
 * Values go in as %1$s, %2$s… so the Hindi sentence can put them in its own word order.
 * A unit test checks that every tr("…") text in the app has a Hindi translation.
 */
fun tr(english: String, vararg args: Any?): String {
    val text = Lang.translate(english)
    return if (args.isEmpty()) text else String.format(Locale.US, text, *args)
}

/** Translates a message that is not written here (a stored sync error, a server reply). Unknown text stays as it is. */
fun trMessage(english: String): String = Lang.translate(english)

/** Marks English text that is stored or sent as it is and translated when shown (trMessage); checked by the catalogue test. */
fun trKey(english: String): String = english
