package app.stocklist.app.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import app.stocklist.app.core.i18n.BilingualName
import app.stocklist.app.core.i18n.Text
import app.stocklist.app.core.i18n.Transliterate
import app.stocklist.app.core.i18n.tr

/**
 * A name in English and in Hindi, in two boxes. Typing English fills the Hindi box with the same name written by sound
 * (Sugar → शुगर), and typing Hindi fills the English box. Either box can be corrected by hand; a box that was changed by
 * hand is not overwritten again. [value] is the saved form, "English / हिन्दी" (see [BilingualName]).
 */
@Composable
fun BilingualNameFields(
    value: String,
    onValueChange: (String) -> Unit,
    title: String? = null,
    required: Boolean = false,
    error: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    modifier: Modifier = Modifier,
) {
    val (english, hindi) = BilingualName.split(value)
    val star = if (required) " *" else ""
    // which box the user typed in last: 1 = English, 2 = Hindi, 0 = neither yet (an existing name was opened)
    var lastTyped by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title + star, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }
        FormField(
            value = english,
            onValueChange = { typed ->
                lastTyped = 1
                val hindiFollows = hindi.isBlank() || hindi == Transliterate.toHindi(english)
                onValueChange(BilingualName.combine(typed, if (hindiFollows) Transliterate.toHindi(typed) else hindi))
            },
            label = tr("English name") + star,
            error = error,
            capitalization = capitalization,
        )
        FormField(
            value = hindi,
            onValueChange = { typed ->
                val englishFollows = english.isBlank() || (lastTyped == 2 && english == Transliterate.toEnglish(hindi))
                lastTyped = 2
                onValueChange(BilingualName.combine(if (englishFollows) Transliterate.toEnglish(typed) else english, typed))
            },
            label = tr("Hindi name") + star,
            capitalization = KeyboardCapitalization.None,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
