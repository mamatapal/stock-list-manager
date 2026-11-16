package app.stocklist.supplieraccount;

import app.stocklist.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Every error the Supplier Management app can receive from the supplier portal API. The constant name is the
 * stable "code" of the problem+json answer; the app shows its own English or Hindi text for each code, and its
 * catalogue test reads this list so no code is ever left without Hindi. Keep the messages short and plain.
 */
public enum SupplierPortalError {
    NAME_REQUIRED(HttpStatus.BAD_REQUEST, "Enter the business name"),
    CONTACT_REQUIRED(HttpStatus.BAD_REQUEST, "Enter the contact person's name"),
    ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "Enter the address, city and state"),
    INVALID_MOBILE(HttpStatus.BAD_REQUEST, "Enter a 10-digit mobile number"),
    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "Enter a valid email address"),
    INVALID_PIN_CODE(HttpStatus.BAD_REQUEST, "Enter a 6-digit PIN code"),
    INVALID_GSTIN(HttpStatus.BAD_REQUEST, "Enter a valid 15-character GSTIN or leave it empty"),
    TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "Some text is too long"),
    WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "Password must have 8 to 128 characters and must not be a common password"),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "The new password must be different from the current one"),
    DEVICE_REQUIRED(HttpStatus.BAD_REQUEST, "Device information is missing"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Wrong email or password. After 5 wrong attempts the account is locked for 15 minutes"),
    WRONG_CURRENT_PASSWORD(HttpStatus.UNAUTHORIZED, "The current password is wrong"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Please sign in again"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "This account is not active"),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please wait a minute and try again"),
    LINK_INVALID(HttpStatus.BAD_REQUEST, "This link is not valid. Please ask for a new one"),
    LINK_EXPIRED(HttpStatus.BAD_REQUEST, "This link has expired. Please ask for a new one"),
    LINK_USED(HttpStatus.BAD_REQUEST, "This link has already been used or replaced by a newer one"),
    DETAILS_LOCKED(HttpStatus.CONFLICT, "Business details cannot be changed after approval"),
    STALE_VERSION(HttpStatus.CONFLICT, "These details were changed elsewhere. Reload and try again"),
    SUPPLIER_NOT_APPROVED(HttpStatus.FORBIDDEN, "Your business is not approved yet"),
    SUPPLIER_SUSPENDED(HttpStatus.FORBIDDEN, "Your business account is suspended");

    private final HttpStatus status;
    private final String message;

    SupplierPortalError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }

    public ApiException ex() {
        return new ApiException(status, name(), message);
    }
}
