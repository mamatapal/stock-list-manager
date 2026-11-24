package app.stocklist.supplier.core.util

import app.stocklist.supplier.core.i18n.asciiDigits
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.data.remote.ProductRequest
import java.math.BigDecimal

/**
 * The checks the server makes on a catalogue product (CatalogueService), so mistakes are shown next to the field
 * before anything is sent: English name (required, at most 150), Hindi name (optional, at most 150), unit (required,
 * at most 30), pack (optional, at most 100), price (required, 0 or more, at most 2 decimals).
 */
object ProductRules {
    const val NAME_MAX = 150
    const val UNIT_MAX = 30
    const val PACK_MAX = 100
    const val REASON_MAX = 500

    /** DECIMAL(15,2) on the server: at most 13 digits before the decimal point. */
    private val PRICE = Regex("""\d{1,13}(\.\d{1,2})?""")

    data class Errors(val nameEn: String?, val nameHi: String?, val unit: String?, val pack: String?, val price: String?) {
        val none: Boolean get() = listOf(nameEn, nameHi, unit, pack, price).all { it == null }
    }

    fun check(nameEn: String, nameHi: String, unit: String, pack: String, price: String): Errors = Errors(
        nameEn = Validators.required(nameEn, NAME_MAX),
        nameHi = Validators.optional(nameHi, NAME_MAX),
        unit = Validators.required(unit, UNIT_MAX),
        pack = Validators.optional(pack, PACK_MAX),
        price = price(price),
    )

    fun price(raw: String): String? {
        val v = cleanPrice(raw)
        return when {
            v.isEmpty() -> tr("Enter the price")
            !PRICE.matches(v) -> tr("Enter the price as a number, for example 240 or 240.50")
            else -> null
        }
    }

    /** Hindi digits become ASCII; spaces and the rupee sign are dropped. */
    fun cleanPrice(raw: String): String = asciiDigits(raw).replace("₹", "").replace(",", "").trim()

    /** The request exactly as the server expects it. Call only when check(...) found no errors. */
    fun request(nameEn: String, nameHi: String, unit: String, pack: String, price: String): ProductRequest =
        ProductRequest(
            nameEn = nameEn.trim(),
            nameHi = nameHi.trim().ifEmpty { null },
            unit = unit.trim(),
            pack = pack.trim().ifEmpty { null },
            price = BigDecimal(cleanPrice(price)).toPlainString(),
        )

    /** A reason or note: optional unless required, at most 500 characters. */
    fun reason(text: String, required: Boolean): String? = when {
        required && text.isBlank() -> tr("Give a reason")
        text.trim().length > REASON_MAX -> tr("At most %1\$s characters", REASON_MAX)
        else -> null
    }
}
