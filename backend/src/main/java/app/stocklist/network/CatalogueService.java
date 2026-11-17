package app.stocklist.network;

import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.Decimals;
import app.stocklist.item.Item;
import app.stocklist.item.ItemDtos.ItemInput;
import app.stocklist.item.ItemRepository;
import app.stocklist.item.ItemService;
import app.stocklist.network.NetworkDtos.CatalogueDto;
import app.stocklist.network.NetworkDtos.CatalogueEntry;
import app.stocklist.network.NetworkDtos.LinkDto;
import app.stocklist.network.NetworkDtos.LinkInput;
import app.stocklist.network.NetworkDtos.NewItemInput;
import app.stocklist.network.NetworkDtos.ProductDto;
import app.stocklist.network.NetworkDtos.ProductInput;
import app.stocklist.permission.Permission;
import app.stocklist.supplieraccount.SupplierAccess;
import app.stocklist.supplieraccount.SupplierOrg;
import app.stocklist.supplieraccount.SupplierPrincipal;
import app.stocklist.unit.UnitService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier catalogues and the shopkeeper's links from supplier products to shop items.
 * <ul>
 *   <li>A supplier manages only its own products; products are deactivated, never deleted.</li>
 *   <li>A shop sees the active products of a supplier it has an ACTIVE connection with, while that supplier is
 *       approved.</li>
 *   <li>Links are made only by the shopkeeper's request (an existing item, or a new item the shopkeeper asked to
 *       create). Names, barcodes and descriptions are never compared, and publishing a product never creates or
 *       changes a shop item.</li>
 * </ul>
 */
@Service
public class CatalogueService {
    private final SupplierProductRepository products;
    private final SupplierProductLinkRepository links;
    private final SupplierConnectionRepository connections;
    private final SupplierAccess supplierAccess;
    private final ItemService items;
    private final ItemRepository itemRows;
    private final UnitService units;
    private final NetworkSupport support;
    private final Clock clock;

    public CatalogueService(SupplierProductRepository products, SupplierProductLinkRepository links,
                            SupplierConnectionRepository connections, SupplierAccess supplierAccess,
                            ItemService items, ItemRepository itemRows, UnitService units, NetworkSupport support,
                            Clock clock) {
        this.products = products;
        this.links = links;
        this.connections = connections;
        this.supplierAccess = supplierAccess;
        this.items = items;
        this.itemRows = itemRows;
        this.units = units;
        this.support = support;
        this.clock = clock;
    }

    // ================================================================== supplier: own catalogue

    @Transactional(readOnly = true)
    public List<ProductDto> ownProducts(SupplierPrincipal caller) {
        supplierAccess.requireApproved(caller);
        return products.findBySupplierOrgIdOrderByNameEnAsc(caller.orgId()).stream().map(CatalogueService::dto).toList();
    }

    @Transactional
    public ProductDto createProduct(SupplierPrincipal caller, ProductInput in) {
        supplierAccess.requireApproved(caller);
        Instant now = Instant.now(clock);
        SupplierProduct p = new SupplierProduct(UUID.randomUUID(), caller.orgId(), now);
        apply(p, in, now);
        return dto(products.saveAndFlush(p));
    }

    /** Changes the catalogue only: orders already placed keep the name, unit and price they were placed with. */
    @Transactional
    public ProductDto updateProduct(SupplierPrincipal caller, UUID id, ProductInput in) {
        supplierAccess.requireApproved(caller);
        SupplierProduct p = own(caller, id);
        Instant now = Instant.now(clock);
        apply(p, in, now);
        return dto(products.saveAndFlush(p));
    }

    @Transactional
    public ProductDto setProductActive(SupplierPrincipal caller, UUID id, boolean active) {
        supplierAccess.requireApproved(caller);
        SupplierProduct p = own(caller, id);
        p.setActive(active, Instant.now(clock));
        return dto(products.saveAndFlush(p));
    }

    private SupplierProduct own(SupplierPrincipal caller, UUID id) {
        return products.findBySupplierOrgIdAndId(caller.orgId(), id)
                .orElseThrow(() -> ApiException.notFound("Product"));
    }

    private static void apply(SupplierProduct p, ProductInput in, Instant now) {
        if (in == null) throw NetworkError.INVALID_REQUEST.ex();
        String nameEn = NetworkSupport.text(in.nameEn(), 150);
        if (nameEn == null) throw NetworkError.PRODUCT_NAME_REQUIRED.ex();
        String unit = NetworkSupport.text(in.unit(), 30);
        if (unit == null) throw NetworkError.PRODUCT_UNIT_REQUIRED.ex();
        BigDecimal price = Decimals.parse(in.price(), "Price", 2, true);
        if (price == null) throw NetworkError.PRODUCT_PRICE_REQUIRED.ex();
        p.apply(nameEn, NetworkSupport.text(in.nameHi(), 150), unit, NetworkSupport.text(in.pack(), 100), price, now);
    }

    // ================================================================== shop: catalogue of a connected supplier

    @Transactional(readOnly = true)
    public CatalogueDto catalogue(AuthUser caller, UUID connectionId) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE, Permission.PURCHASE_ORDER_MANAGE);
        SupplierConnection c = eligibleConnection(caller, connectionId);
        Map<UUID, SupplierProductLink> byProduct = new HashMap<>();
        for (SupplierProductLink l : links.findByBusinessIdAndSupplierOrgId(caller.businessId(), c.getSupplierOrgId())) {
            byProduct.put(l.getSupplierProductId(), l);
        }
        List<CatalogueEntry> entries = products.findBySupplierOrgIdAndActiveTrueOrderByNameEnAsc(c.getSupplierOrgId())
                .stream().map(p -> new CatalogueEntry(dto(p), linkDto(caller, byProduct.get(p.getId())))).toList();
        return new CatalogueDto(support.supplierSummary(c.getSupplierOrgId()), entries);
    }

    /**
     * Creates or changes the shopkeeper's link for one product of a connected supplier. A new item is created only
     * when the request asks for it (newItem), through the normal item rules (ITEM_ADD).
     */
    @Transactional
    public CatalogueEntry putLink(AuthUser caller, UUID connectionId, UUID productId, LinkInput in) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE);
        SupplierConnection c = eligibleConnection(caller, connectionId);
        SupplierProduct product = products.findBySupplierOrgIdAndId(c.getSupplierOrgId(), productId)
                .filter(SupplierProduct::isActive)
                .orElseThrow(() -> ApiException.notFound("Product"));
        if (in == null || (in.itemId() == null) == (in.newItem() == null)) throw NetworkError.LINK_CHOICE.ex();
        UUID bid = caller.businessId();
        BigDecimal perPack = in.unitsPerPack() == null || in.unitsPerPack().isBlank() ? BigDecimal.ONE
                : Decimals.parse(in.unitsPerPack(), "Units per pack", 3, false);
        Item item;
        if (in.itemId() != null) {
            item = items.requireUsable(bid, in.itemId());
        } else {
            NewItemInput n = in.newItem();
            String name = NetworkSupport.text(n.name(), 150);
            if (name == null || n.defaultUnitId() == null) throw NetworkError.LINK_CHOICE.ex();
            UUID newId = UUID.randomUUID();
            items.create(caller, newId, new ItemInput(name, NetworkSupport.text(n.category(), 80), n.defaultUnitId(),
                    null, null, null, null, true));
            item = items.requireUsable(bid, newId);
        }
        UUID unitId = in.unitId() != null ? in.unitId() : item.getDefaultUnitId();
        units.requireUsable(bid, unitId);
        Instant now = Instant.now(clock);
        SupplierProductLink link = links.findByBusinessIdAndSupplierProductId(bid, product.getId())
                .orElseGet(() -> new SupplierProductLink(UUID.randomUUID(), bid, c.getSupplierOrgId(), product.getId(),
                        caller.userId(), now));
        link.apply(item.getId(), unitId, perPack, caller.userId(), now);
        link = links.saveAndFlush(link);
        return new CatalogueEntry(dto(product), linkDto(caller, link));
    }

    /** Removes the shopkeeper's link (orders already placed keep their own copy of it). */
    @Transactional
    public void deleteLink(AuthUser caller, UUID connectionId, UUID productId) {
        NetworkSupport.requireAny(caller, Permission.SUPPLIER_NETWORK_MANAGE);
        SupplierConnection c = connections.findByBusinessIdAndId(caller.businessId(), connectionId)
                .orElseThrow(() -> ApiException.notFound("Connection"));
        SupplierProductLink link = links.findByBusinessIdAndSupplierProductId(caller.businessId(), productId)
                .filter(l -> l.getSupplierOrgId().equals(c.getSupplierOrgId()))
                .orElseThrow(() -> ApiException.notFound("Product link"));
        links.delete(link);
        links.flush();
    }

    /** The shop's own ACTIVE connection with a supplier that is still approved; 404 for anything else of theirs. */
    private SupplierConnection eligibleConnection(AuthUser caller, UUID connectionId) {
        SupplierConnection c = connections.findByBusinessIdAndId(caller.businessId(), connectionId)
                .orElseThrow(() -> ApiException.notFound("Connection"));
        if (!c.isActive()) throw NetworkError.CONNECTION_NOT_ACTIVE.ex();
        SupplierOrg o = support.org(c.getSupplierOrgId());
        if (!o.isApproved()) throw NetworkError.SUPPLIER_NOT_AVAILABLE.ex();
        return c;
    }

    // ================================================================== conversions

    static ProductDto dto(SupplierProduct p) {
        return new ProductDto(p.getId(), p.getNameEn(), p.getNameHi(), p.getUnit(), p.getPack(),
                NetworkSupport.money(p.getPrice()), p.isActive(), p.getVersion() == null ? 0 : p.getVersion(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private LinkDto linkDto(AuthUser caller, SupplierProductLink l) {
        if (l == null) return null;
        String itemName = itemRows.findByBusinessIdAndId(caller.businessId(), l.getItemId()).map(Item::getName)
                .orElse(null);
        return new LinkDto(l.getId(), l.getItemId(), itemName, l.getUnitId(), NetworkSupport.qty(l.getUnitsPerPack()),
                l.getVersion() == null ? 0 : l.getVersion(), l.getUpdatedAt());
    }
}
