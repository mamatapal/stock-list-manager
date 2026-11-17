package app.stocklist.network;

import app.stocklist.common.security.CurrentUser;
import app.stocklist.network.NetworkDtos.CatalogueDto;
import app.stocklist.network.NetworkDtos.CatalogueEntry;
import app.stocklist.network.NetworkDtos.ConnectionRequest;
import app.stocklist.network.NetworkDtos.ContactLinkInput;
import app.stocklist.network.NetworkDtos.DirectoryEntry;
import app.stocklist.network.NetworkDtos.LinkInput;
import app.stocklist.network.NetworkDtos.OrderInput;
import app.stocklist.network.NetworkDtos.ReasonInput;
import app.stocklist.network.NetworkDtos.ReceiptDto;
import app.stocklist.network.NetworkDtos.ReceiptInput;
import app.stocklist.network.NetworkDtos.ShopConnectionDto;
import app.stocklist.network.NetworkDtos.ShopOrderDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supplier network for shops (Stock List Manager app). Shop tokens only; the business comes from the token, never
 * from the request. SUPPLIER_NETWORK_MANAGE: directory, connections, supplier contact link, product links.
 * PURCHASE_ORDER_MANAGE: purchase orders and goods receipt (receipt also follows the purchase permissions).
 * Connections and catalogues can be read with either. Permissions are checked before anything else.
 */
@RestController
@RequestMapping("/api/v1/supplier-network")
@Tag(name = "Supplier network (shop)", description = "Find approved suppliers, connect, browse their catalogue, link "
        + "products to your items, place purchase orders and receive the goods. SUPPLIER_NETWORK_MANAGE / "
        + "PURCHASE_ORDER_MANAGE")
public class ShopNetworkController {
    private final ConnectionService connections;
    private final CatalogueService catalogue;
    private final PurchaseOrderService orders;

    public ShopNetworkController(ConnectionService connections, CatalogueService catalogue,
                                 PurchaseOrderService orders) {
        this.connections = connections;
        this.catalogue = catalogue;
        this.orders = orders;
    }

    // ------------------------------------------------------------------ directory

    @Operation(summary = "Approved suppliers that chose to be listed, by name, city or state (at most 100)")
    @GetMapping("/directory")
    public List<DirectoryEntry> directory(@RequestParam(required = false) String q) {
        return connections.directory(CurrentUser.get(), q);
    }

    @Operation(summary = "Public details of one listed supplier, and this shop's connection status with it")
    @GetMapping("/directory/{supplierOrgId}")
    public DirectoryEntry directoryEntry(@PathVariable UUID supplierOrgId) {
        return connections.directoryEntry(CurrentUser.get(), supplierOrgId);
    }

    // ------------------------------------------------------------------ connections

    @Operation(summary = "This shop's supplier connections and requests, newest first")
    @GetMapping("/connections")
    public List<ShopConnectionDto> connections() {
        return connections.shopConnections(CurrentUser.get());
    }

    @Operation(summary = "Ask a listed, approved supplier to connect (the supplier accepts or declines). Optional "
            + "supplierId links one of your supplier contacts")
    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public ShopConnectionDto request(@RequestBody(required = false) ConnectionRequest body) {
        return connections.request(CurrentUser.get(), body);
    }

    @Operation(summary = "One connection with its history")
    @GetMapping("/connections/{connectionId}")
    public ShopConnectionDto connection(@PathVariable UUID connectionId) {
        return connections.shopConnection(CurrentUser.get(), connectionId);
    }

    @Operation(summary = "Withdraw a request the supplier has not answered")
    @PostMapping("/connections/{connectionId}/cancel")
    public ShopConnectionDto cancelRequest(@PathVariable UUID connectionId) {
        return connections.cancelRequest(CurrentUser.get(), connectionId);
    }

    @Operation(summary = "End an active connection (optional reason). Orders not yet dispatched are cancelled; "
            + "dispatched orders can still be received")
    @PostMapping("/connections/{connectionId}/end")
    public ShopConnectionDto end(@PathVariable UUID connectionId, @RequestBody(required = false) ReasonInput body) {
        return connections.endByShop(CurrentUser.get(), connectionId, body);
    }

    @Operation(summary = "Link the supplier to one of your supplier contacts (supplierId), or create a new contact "
            + "for it (createContact: true; needs SUPPLIER_ADD). Needed before goods can be received")
    @PutMapping("/connections/{connectionId}/contact")
    public ShopConnectionDto linkContact(@PathVariable UUID connectionId,
                                         @RequestBody(required = false) ContactLinkInput body) {
        return connections.linkContact(CurrentUser.get(), connectionId, body);
    }

    // ------------------------------------------------------------------ catalogue and product links

    @Operation(summary = "Active products of a connected supplier, each with your link to a shop item (or none)")
    @GetMapping("/connections/{connectionId}/catalogue")
    public CatalogueDto catalogue(@PathVariable UUID connectionId) {
        return catalogue.catalogue(CurrentUser.get(), connectionId);
    }

    @Operation(summary = "Link a supplier product to one of your items (itemId), or to a new item you create here "
            + "(newItem; needs ITEM_ADD). Never done automatically")
    @PutMapping("/connections/{connectionId}/products/{productId}/link")
    public CatalogueEntry putLink(@PathVariable UUID connectionId, @PathVariable UUID productId,
                                  @RequestBody(required = false) LinkInput body) {
        return catalogue.putLink(CurrentUser.get(), connectionId, productId, body);
    }

    @Operation(summary = "Remove your link for a supplier product (orders already placed keep their copy)")
    @DeleteMapping("/connections/{connectionId}/products/{productId}/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(@PathVariable UUID connectionId, @PathVariable UUID productId) {
        catalogue.deleteLink(CurrentUser.get(), connectionId, productId);
    }

    // ------------------------------------------------------------------ purchase orders

    @Operation(summary = "This shop's purchase orders, newest first (optional status filter)")
    @GetMapping("/orders")
    public List<ShopOrderDto> orders(@RequestParam(required = false) String status) {
        return orders.shopOrders(CurrentUser.get(), status);
    }

    @Operation(summary = "Place a purchase order with a connected supplier. Every product must be linked to one of "
            + "your items. An optional id makes a repeated request place the order once")
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ShopOrderDto create(@RequestBody(required = false) OrderInput body) {
        return orders.create(CurrentUser.get(), body);
    }

    @Operation(summary = "One purchase order with its lines and history")
    @GetMapping("/orders/{orderId}")
    public ShopOrderDto order(@PathVariable UUID orderId) {
        return orders.shopOrder(CurrentUser.get(), orderId);
    }

    @Operation(summary = "Cancel an order that has not been dispatched (optional reason)")
    @PostMapping("/orders/{orderId}/cancel")
    public ShopOrderDto cancel(@PathVariable UUID orderId, @RequestBody(required = false) ReasonInput body) {
        return orders.cancel(CurrentUser.get(), orderId, body);
    }

    @Operation(summary = "Receive the goods (once per order): actual quantities, batches and expiry dates. Creates a "
            + "completed purchase with one delivery through the normal purchase rules; only this changes stock")
    @PostMapping("/orders/{orderId}/receive")
    public ReceiptDto receive(@PathVariable UUID orderId, @RequestBody(required = false) ReceiptInput body) {
        return orders.receive(CurrentUser.get(), orderId, body);
    }
}
