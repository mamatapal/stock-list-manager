package app.stocklist.supplier.core.util

import app.stocklist.supplier.core.i18n.tr

/**
 * The same checks the server makes (SupplierInputs on the backend), so most mistakes are shown next to the field
 * before anything is sent. Each returns the error text, or null when the value is fine.
 */
object Validators {
    private val MOBILE = Regex("[6-9][0-9]{9}")
    private val PIN = Regex("[1-9][0-9]{5}")
    private val GSTIN = Regex("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]")
    private val EMAIL = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}")

    /** 10 digits; spaces, dashes and a leading +91 or 0 are removed (as on the server). */
    fun cleanMobile(raw: String): String {
        var v = raw.replace(Regex("[\\s-]"), "")
        if (v.startsWith("+91")) v = v.substring(3) else if (v.length == 11 && v.startsWith("0")) v = v.substring(1)
        return v
    }

    fun required(value: String, max: Int): String? = when {
        value.isBlank() -> tr("Required")
        value.trim().length > max -> tr("At most %1\$s characters", max)
        else -> null
    }

    fun optional(value: String, max: Int): String? =
        if (value.trim().length > max) tr("At most %1\$s characters", max) else null

    fun mobile(raw: String): String? =
        if (MOBILE.matches(cleanMobile(raw))) null else tr("Enter a 10-digit mobile number")

    fun email(raw: String): String? {
        val v = raw.trim()
        return if (v.length <= 254 && EMAIL.matches(v)) null else tr("Enter a valid email address")
    }

    fun pinCode(raw: String): String? = if (PIN.matches(raw.trim())) null else tr("Enter a 6-digit PIN code")

    fun gstin(raw: String): String? {
        val v = raw.trim().uppercase()
        return if (v.isEmpty() || GSTIN.matches(v)) null else tr("Enter a valid 15-character GSTIN or leave it empty")
    }

    fun password(value: String): String? =
        if (value.length in 8..128) null else tr("Use 8 to 128 characters")

    fun confirm(password: String, again: String): String? =
        if (password == again) null else tr("The two passwords are not the same")
}
