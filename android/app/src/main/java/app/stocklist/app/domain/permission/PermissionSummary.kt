package app.stocklist.app.domain.permission

import app.stocklist.app.core.i18n.tr

/** Turns a permission set into readable groups for the staff dashboard ("What you can do"). */
object PermissionSummary {
    fun grouped(permissions: Set<Permission>): List<Pair<String, List<String>>> =
        Permission.groups.mapNotNull { group ->
            val labels = Permission.entries.filter { it.group == group && it in permissions }.map { it.label }
            if (labels.isEmpty()) null else group to labels
        }

    /** Human wording for audit-log actions. */
    fun actionLabel(action: String): String = when (action) {
        "BUSINESS_REGISTERED" -> tr("Registered the business")
        "BUSINESS_UPDATED" -> tr("Changed business details")
        "LOGIN" -> tr("Signed in")
        "LOGIN_FAILED" -> tr("Failed sign-in attempt")
        "ACCOUNT_LOCKED" -> tr("Account locked after failed attempts")
        "LOGOUT" -> tr("Signed out")
        "TOKEN_REUSE_DETECTED" -> tr("Suspicious session reuse - session ended")
        "REAUTHENTICATED" -> tr("Confirmed password")
        "PASSWORD_CHANGED" -> tr("Changed password")
        "PASSWORD_RESET_REQUESTED" -> tr("Requested a password reset")
        "PASSWORD_RESET" -> tr("Reset password")
        "STAFF_CREATED" -> tr("Created a staff account")
        "STAFF_UPDATED" -> tr("Edited a staff account")
        "STAFF_ACTIVATED" -> tr("Activated a staff account")
        "STAFF_DEACTIVATED" -> tr("Deactivated a staff account")
        "STAFF_PASSWORD_RESET" -> tr("Reset a staff password")
        "STAFF_SESSIONS_REVOKED" -> tr("Signed a staff member out everywhere")
        "PERMISSIONS_CHANGED" -> tr("Changed staff permissions")
        else -> action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
