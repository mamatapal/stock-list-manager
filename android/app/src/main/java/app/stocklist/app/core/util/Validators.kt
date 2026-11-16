package app.stocklist.app.core.util

import app.stocklist.app.core.i18n.tr

/** Same rules as the server (backend Patterns.java), so most mistakes are caught before sending. */
object Validators {
    private val USERNAME = Regex("^[A-Za-z0-9._]{3,40}$")
    private val MOBILE = Regex("^[6-9][0-9]{9}$")
    private val PIN = Regex("^[1-9][0-9]{5}$")
    private val GSTIN = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun required(value: String, label: String): String? =
        if (value.isBlank()) tr("%1\$s is required", label) else null

    fun username(value: String): String? = when {
        value.isBlank() -> tr("Username is required")
        !USERNAME.matches(value.trim()) -> tr("3-40 letters, digits, dots or underscores")
        else -> null
    }

    fun mobile(value: String, required: Boolean = true): String? = when {
        value.isBlank() -> if (required) tr("Mobile number is required") else null
        !MOBILE.matches(value.trim()) -> tr("Enter a 10-digit mobile number")
        else -> null
    }

    /** Optional landline or mobile: 6-12 digits, optionally starting with +91 (server: Patterns.MOBILE_OR_PHONE). */
    fun phone(value: String): String? = when {
        value.isBlank() -> null
        !Regex("^(\\+91)?[0-9]{6,12}$").matches(value.trim()) -> tr("Enter a valid phone number")
        else -> null
    }

    fun pinCode(value: String): String? = when {
        value.isBlank() -> tr("PIN code is required")
        !PIN.matches(value.trim()) -> tr("Enter a 6-digit PIN code")
        else -> null
    }

    fun gstin(value: String): String? = when {
        value.isBlank() -> null
        !GSTIN.matches(value.trim().uppercase()) -> tr("Enter a valid 15-character GSTIN")
        else -> null
    }

    fun email(value: String): String? = when {
        value.isBlank() -> tr("Email is required")
        !EMAIL.matches(value.trim()) -> tr("Enter a valid email address")
        else -> null
    }

    fun newPassword(value: String, username: String?): String? = when {
        value.length < 8 -> tr("At least 8 characters")
        value.length > 128 -> tr("At most 128 characters")
        username != null && value.equals(username, ignoreCase = true) -> tr("Must not be the same as the username")
        else -> null
    }
}
