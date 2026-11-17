package app.stocklist.network;

import app.stocklist.network.NetworkDtos.ListingDto;
import app.stocklist.network.NetworkDtos.ListingInput;
import app.stocklist.network.NetworkDtos.ProductDto;
import app.stocklist.network.NetworkDtos.ProductInput;
import app.stocklist.network.NetworkDtos.ReasonInput;
import app.stocklist.network.NetworkDtos.SupplierConnectionDto;
import app.stocklist.network.NetworkDtos.SupplierOrderDto;
import app.stocklist.supplieraccount.SupplierPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
 * Supplier network for suppliers (Supplier Management app): directory listing, connection requests, catalogue and
 * incoming purchase orders. Supplier tokens only; the organisation comes from the token. Every call needs the
 * organisation to be APPROVED (checked in the database each time, so a suspension applies at once), and only its own
 * connections, products and orders are reachable.
 */
@RestController
@RequestMapping("/api/v1/supplier-portal")
@Tag(name = "Supplier network (supplier)", description = "Supplier Management app: directory listing, connection "
        + "requests, catalogue and purchase orders. Approved suppliers only")
public class SupplierNetworkController {
    private final ConnectionService connections;
    private final CatalogueService catalogue;
    private final PurchaseOrderService orders;

    public SupplierNetworkController(ConnectionService connections, CatalogueService catalogue,
                                     PurchaseOrderService orders) {
        this.connections = connections;
        this.catalogue = catalogue;
        this.orders = orders;
    }

    // ------------------------------------------------------------------ directory listing

    @Operation(summary = "Whether shops can find this business in the supplier directory (off until turned on)")
    @GetMapping("/directory-listing")
    public ListingDto listing() {
        return connections.listing(SupplierPrincipal.current());
    }

    @Operation(summary = "Turn the directory listing on or off. Existing connections and orders are not affected")
    @PutMapping("/directory-listing")
    public ListingDto setListing(@RequestBody(required = false) ListingInput body) {
        return connections.setListing(SupplierPrincipal.current(), body);
    }

    // ------------------------------------------------------------------ connections

    @Operation(summary = "Connection requests and connections with shops, newest first (optional status filter)")
    @GetMapping("/connections")
    public List<SupplierConnectionDto> connections(@RequestParam(required = false) String status) {
        return connections.supplierConnections(SupplierPrincipal.current(), status);
    }

    @Operation(summary = "One connection with its history")
    @GetMapping("/connections/{connectionId}")
    public SupplierConnectionDto connection(@PathVariable UUID connectionId) {
        return connections.supplierConnection(SupplierPrincipal.current(), connectionId);
    }

    @Operation(summary = "Accept a shop's connection request")
    @PostMapping("/connections/{connectionId}/accept")
    public SupplierConnectionDto acceptConnection(@PathVariable UUID connectionId) {
        return connections.accept(SupplierPrincipal.current(), connectionId);
    }

    @Operation(summary = "Decline a shop's connection request (optional reason)")
    @PostMapping("/connections/{connectionId}/decline")
    public SupplierConnectionDto decline(@PathVariable UUID connectionId,
                                         @RequestBody(required = false) ReasonInput body) {
        return connections.decline(SupplierPrincipal.current(), connectionId, body);
    }

    @Operation(summary = "End an active connection (optional reason). Orders not yet dispatched are cancelled")
    @PostMapping("/connections/{connectionId}/end")
    public SupplierConnectionDto end(@PathVariable UUID connectionId, @RequestBody(required = false) ReasonInput body) {
        return connections.endBySupplier(SupplierPrincipal.current(), connectionId, body);
    }

    // ------------------------------------------------------------------ catalogue

    @Operation(summary = "This business's catalogue, active and inactive products")
    @GetMapping("/products")
    public List<ProductDto> products() {
        return catalogue.ownProducts(SupplierPrincipal.current());
    }

    @Operation(summary = "Add a product: English name, optional Hindi name, unit, optional pack, price")
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto createProduct(@RequestBody(required = false) ProductInput body) {
        return catalogue.createProduct(SupplierPrincipal.current(), body);
    }

    @Operation(summary = "Change a product. Orders already placed keep the name, unit and price they were placed with")
    @PutMapping("/products/{productId}")
    public ProductDto updateProduct(@PathVariable UUID productId, @RequestBody(required = false) ProductInput body) {
        return catalogue.updateProduct(SupplierPrincipal.current(), productId, body);
    }

    @Operation(summary = "Stop offering a product (shops no longer see it; old orders keep it)")
    @PostMapping("/products/{productId}/deactivate")
    public ProductDto deactivate(@PathVariable UUID productId) {
        return catalogue.setProductActive(SupplierPrincipal.current(), productId, false);
    }

    @Operation(summary = "Offer a deactivated product again")
    @PostMapping("/products/{productId}/activate")
    public ProductDto activate(@PathVariable UUID productId) {
        return catalogue.setProductActive(SupplierPrincipal.current(), productId, true);
    }

    // ------------------------------------------------------------------ purchase orders

    @Operation(summary = "Purchase orders from connected shops, newest first (optional status filter)")
    @GetMapping("/orders")
    public List<SupplierOrderDto> orders(@RequestParam(required = false) String status) {
        return orders.supplierOrders(SupplierPrincipal.current(), status);
    }

    @Operation(summary = "One purchase order with its lines and history")
    @GetMapping("/orders/{orderId}")
    public SupplierOrderDto order(@PathVariable UUID orderId) {
        return orders.supplierOrder(SupplierPrincipal.current(), orderId);
    }

    @Operation(summary = "Accept a new order")
    @PostMapping("/orders/{orderId}/accept")
    public SupplierOrderDto acceptOrder(@PathVariable UUID orderId) {
        return orders.accept(SupplierPrincipal.current(), orderId);
    }

    @Operation(summary = "Reject a new order; a reason is required")
    @PostMapping("/orders/{orderId}/reject")
    public SupplierOrderDto reject(@PathVariable UUID orderId, @RequestBody(required = false) ReasonInput body) {
        return orders.reject(SupplierPrincipal.current(), orderId, body);
    }

    @Operation(summary = "Mark an accepted order as dispatched (optional note)")
    @PostMapping("/orders/{orderId}/dispatch")
    public SupplierOrderDto dispatch(@PathVariable UUID orderId, @RequestBody(required = false) ReasonInput body) {
        return orders.dispatch(SupplierPrincipal.current(), orderId, body);
    }
}
