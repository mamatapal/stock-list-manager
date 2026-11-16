package app.stocklist.supplier.core.i18n

/**
 * Hindi keyboards can type Devanagari digits (०१२३४५६७८९). Quantities, prices, phone numbers and PIN codes are kept
 * with 0-9, so these (and any other script's digits) are turned into 0-9 as they are typed.
 */
fun asciiDigits(text: String): String {
    if (text.all { it in '0'..'9' || !it.isDigit() }) return text
    return buildString(text.length) {
        for (c in text) append(if (c.isDigit() && c !in '0'..'9') ('0' + Character.digit(c, 10)) else c)
    }
}
