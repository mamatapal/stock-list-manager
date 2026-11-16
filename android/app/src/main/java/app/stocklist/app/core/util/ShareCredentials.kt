package app.stocklist.app.core.util

import app.stocklist.app.core.i18n.tr

import android.content.Context
import android.content.Intent

/**
 * Opens Android's share sheet (WhatsApp, SMS, Gmail, ...) with a staff member's sign-in details.
 * Only the one-time temporary password is ever shared; the app forces the staff member to replace it
 * at first sign-in, so the shared message stops working as soon as they sign in.
 */
object ShareCredentials {
    fun message(businessName: String, fullName: String, username: String, temporaryPassword: String): String =
        """
        Hello $fullName,

        Your Stock List Manager account for $businessName is ready.

        Username: $username
        Temporary password: $temporaryPassword

        Open the Stock List Manager app, tap "Staff sign-in" and sign in with these details.
        You will be asked to choose your own password straight away. Please don't share this message.
        """.trimIndent()

    fun share(context: Context, businessName: String, fullName: String, username: String, temporaryPassword: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, tr("Your Stock List Manager sign-in"))
            putExtra(Intent.EXTRA_TEXT, message(businessName, fullName, username, temporaryPassword))
        }
        context.startActivity(Intent.createChooser(send, tr("Share sign-in details")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
