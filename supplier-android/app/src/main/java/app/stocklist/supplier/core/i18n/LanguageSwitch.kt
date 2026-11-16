package app.stocklist.supplier.core.i18n

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** English | हिन्दी. Each option is written in its own language so anyone can find theirs. */
@Composable
fun LanguageSwitch(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier) {
        SingleChoiceSegmentedButtonRow {
            AppLanguage.entries.forEachIndexed { index, language ->
                SegmentedButton(
                    selected = Lang.language == language,
                    onClick = { Lang.set(context.applicationContext, language) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppLanguage.entries.size),
                ) { Text(language.nativeName) }
            }
        }
    }
}
