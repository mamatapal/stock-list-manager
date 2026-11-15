package app.stocklist.app.domain.permission

/** Turns a permission set into readable groups for the staff dashboard ("What you can do"). */
object PermissionSummary {
    fun grouped(permissions: Set<Permission>): List<Pair<String, List<String>>> =
        Permission.groups.mapNotNull { group ->
            val labels = Permission.entries.filter { it.group == group && it in permissions }.map { it.label }
            if (labels.isEmpty()) null else group to labels
        }

    /** Human wording for audit-log actions. */
    fun actionLabel(action: String): String = when (action) {
        "BUSINESS_REGISTERED" -> "Registered the business"
        "BUSINESS_UPDATED" -> "Changed business details"
        "LOGIN" -> "Signed in"
        "LOGIN_FAILED" -> "Failed sign-in attempt"
        "ACCOUNT_LOCKED" -> "Account locked after failed attempts"
        "LOGOUT" -> "Signed out"
        "TOKEN_REUSE_DETECTED" -> "Suspicious session reuse - session ended"
        "REAUTHENTICATED" -> "Confirmed password"
        "PASSWORD_CHANGED" -> "Changed password"
        "PASSWORD_RESET_REQUESTED" -> "Requested a password reset"
        "PASSWORD_RESET" -> "Reset password"
        "STAFF_CREATED" -> "Created a staff account"
        "STAFF_UPDATED" -> "Edited a staff account"
        "STAFF_ACTIVATED" -> "Activated a staff account"
        "STAFF_DEACTIVATED" -> "Deactivated a staff account"
        "STAFF_PASSWORD_RESET" -> "Reset a staff password"
        "STAFF_SESSIONS_REVOKED" -> "Signed a staff member out everywhere"
        "PERMISSIONS_CHANGED" -> "Changed staff permissions"
        else -> action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
