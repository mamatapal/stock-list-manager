package app.stocklist.common.audit;

public final class AuditAction {
    private AuditAction() {}

    public static final String BUSINESS_REGISTERED = "BUSINESS_REGISTERED";
    public static final String BUSINESS_UPDATED = "BUSINESS_UPDATED";
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String LOGOUT = "LOGOUT";
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
    public static final String REAUTHENTICATED = "REAUTHENTICATED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String STAFF_CREATED = "STAFF_CREATED";
    public static final String STAFF_UPDATED = "STAFF_UPDATED";
    public static final String STAFF_ACTIVATED = "STAFF_ACTIVATED";
    public static final String STAFF_DEACTIVATED = "STAFF_DEACTIVATED";
    public static final String STAFF_PASSWORD_RESET = "STAFF_PASSWORD_RESET";
    public static final String STAFF_SESSIONS_REVOKED = "STAFF_SESSIONS_REVOKED";
    public static final String PERMISSIONS_CHANGED = "PERMISSIONS_CHANGED";
}
