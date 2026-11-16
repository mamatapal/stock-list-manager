package app.stocklist.supplier.core.i18n

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.material3.Text as MaterialText

/**
 * Drop-in for Material's Text (same parameters). Text in two languages is shown with the main language first and the
 * other language underneath, a little smaller and lighter. With maxLines = 1 (labels, chips) both go on one line:
 * "हिन्दी / English". Text with no translation (names, numbers) is shown as it is.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val (main, other) = Bilingual.lines(text)
    if (other == null || maxLines == 1) {
        MaterialText(if (other == null) main else "$main / $other", modifier, color, fontSize, fontStyle, fontWeight,
            fontFamily, letterSpacing, textDecoration, textAlign, lineHeight, overflow, softWrap, maxLines, minLines,
            onTextLayout, style)
        return
    }
    val base = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val twoLines = buildAnnotatedString {
        append(main)
        append('\n')
        withStyle(SpanStyle(fontSize = 0.85.em, color = base.copy(alpha = base.alpha * 0.72f), fontWeight = FontWeight.Normal)) {
            append(other)
        }
    }
    MaterialText(twoLines, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration,
        textAlign, lineHeight, overflow, softWrap, if (maxLines == Int.MAX_VALUE) maxLines else maxLines + 1, minLines,
        emptyMap(), onTextLayout ?: {}, style)
}
