package app.stocklist.network;

import app.stocklist.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Errors of the supplier network (directory, connections, catalogue, product links, purchase orders, receipt), for
 * both the shop API and the supplier portal. The constant name is the stable "code" of the problem+json answer.
 * Kept apart from SupplierPortalError, whose codes the Supplier Management app must all translate already; the apps
 * add their English and Hindi texts for these codes in their own pull requests.
 */
public enum NetworkError {
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "You do not have permission for this action"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Some fields are missing or invalid"),
    TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "Some text is too long"),
    REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Give a reason"),

    CONNECTION_EXISTS(HttpStatus.CONFLICT, "You already have a request or a connection with this supplier"),
    CONNECTION_NOT_ACTIVE(HttpStatus.CONFLICT, "This supplier connection is not active"),
    CONNECTION_STATUS(HttpStatus.CONFLICT, "This connection cannot change from its current status"),
    SUPPLIER_NOT_AVAILABLE(HttpStatus.CONFLICT, "This supplier cannot take new orders right now"),
    CONTACT_CHOICE(HttpStatus.BAD_REQUEST, "Choose one of your supplier contacts, or create a new one"),
    CONTACT_IN_USE(HttpStatus.CONFLICT, "This supplier contact is already linked to another supplier"),
    CONTACT_NOT_LINKED(HttpStatus.CONFLICT, "Link this supplier to one of your supplier contacts before receiving goods"),

    PRODUCT_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "Enter the product name"),
    PRODUCT_UNIT_REQUIRED(HttpStatus.BAD_REQUEST, "Enter the unit, for example kg or box"),
    PRODUCT_PRICE_REQUIRED(HttpStatus.BAD_REQUEST, "Enter the price"),
    PRODUCT_NOT_AVAILABLE(HttpStatus.CONFLICT, "A product is not available from this supplier any more"),
    LINK_CHOICE(HttpStatus.BAD_REQUEST, "Choose one of your items, or create a new one"),
    PRODUCT_NOT_LINKED(HttpStatus.CONFLICT, "Link every product to one of your items before ordering"),

    EMPTY_ORDER(HttpStatus.BAD_REQUEST, "Add at least one product to the order"),
    TOO_MANY_LINES(HttpStatus.BAD_REQUEST, "At most 200 products per order"),
    DUPLICATE_PRODUCT(HttpStatus.BAD_REQUEST, "The same product appears twice in the order"),
    ORDER_STATUS(HttpStatus.CONFLICT, "This order cannot change from its current status"),
    ORDER_ALREADY_RECEIVED(HttpStatus.CONFLICT, "The goods for this order have already been received"),
    ORDER_ID_REUSED(HttpStatus.CONFLICT, "This order id was already used for a different order"),
    RECEIPT_LINES(HttpStatus.BAD_REQUEST, "Enter the received quantity for every line of the order, once"),
    RECEIVED_TOO_MUCH(HttpStatus.BAD_REQUEST, "A received quantity is more than the quantity ordered"),
    NOTHING_RECEIVED(HttpStatus.BAD_REQUEST, "Enter at least one received quantity"),
    QUANTITY_PRECISION(HttpStatus.BAD_REQUEST, "A received quantity gives more than 3 decimal places in your unit");

    private final HttpStatus status;
    private final String message;

    NetworkError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }

    public ApiException ex() {
        return new ApiException(status, name(), message);
    }
}
