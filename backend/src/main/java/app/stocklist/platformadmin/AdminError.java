package app.stocklist.platformadmin;

import app.stocklist.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Errors of the Supplier Administration portal (English only). The constant name is the problem+json "code". */
public enum AdminError {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Please sign in"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,
            "Wrong username, password or authenticator code. After 5 failures the account is locked for 15 minutes"),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please wait a minute and try again"),
    CSRF_HEADER_MISSING(HttpStatus.FORBIDDEN, "Request refused: please use the Supplier Administration page"),
    SUPPLIER_NOT_FOUND(HttpStatus.NOT_FOUND, "Supplier not found"),
    STALE_VERSION(HttpStatus.CONFLICT, "This supplier was changed meanwhile. Reload and try again"),
    WRONG_STATUS(HttpStatus.CONFLICT, "This action is not possible in the supplier's current status"),
    REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Please enter a reason (it is shown to the supplier)"),
    REASON_TOO_LONG(HttpStatus.BAD_REQUEST, "The reason must be at most 500 characters"),
    INVALID_STATUS_FILTER(HttpStatus.BAD_REQUEST, "Unknown status filter");

    private final HttpStatus status;
    private final String message;

    AdminError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public ApiException ex() {
        return new ApiException(status, name(), message);
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
