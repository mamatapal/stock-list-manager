package app.stocklist.purchase;

import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.Decimals;
import app.stocklist.item.ItemRepository;
import app.stocklist.purchase.PurchaseDtos.PurchaseDto;
import app.stocklist.supplier.Supplier;
import app.stocklist.supplier.SupplierRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier-wise and item-wise purchase history. Everything shown comes from the purchase lines' snapshots, so
 * historical quantities and prices are exactly what was billed. Drafts are not history; cancelled purchases are
 * listed (marked) but never counted in totals.
 */
@Service
public class HistoryService {
    private final PurchaseRepository purchases;
    private final PurchaseLineRepository lines;
    private final PurchaseService purchaseService;
    private final ReceivingService receiving;
    private final SupplierRepository suppliers;
    private final ItemRepository items;

    public HistoryService(PurchaseRepository purchases, PurchaseLineRepository lines, PurchaseService purchaseService,
                          ReceivingService receiving, SupplierRepository suppliers, ItemRepository items) {
        this.purchases = purchases;
        this.lines = lines;
        this.purchaseService = purchaseService;
        this.receiving = receiving;
        this.suppliers = suppliers;
        this.items = items;
    }

    public record SupplierHistory(UUID supplierId, String supplierName, int purchaseCount, String totalCost,
                                  LocalDate firstPurchaseDate, LocalDate lastPurchaseDate,
                                  List<PurchaseDto> purchases) {}

    public record ItemPurchaseRow(UUID purchaseId, UUID purchaseLineId, LocalDate purchaseDate, String status,
                                  UUID supplierId, String supplierName, String invoiceNo, String itemName,
                                  String quantity, String unitCode, String priceBasis, String purchasePrice,
                                  String purchaseUnitPrice, String sellingPrice, String totalCost,
                                  String receivedQuantity, UUID createdBy, UUID updatedBy) {}

    public record ItemHistory(UUID itemId, int purchaseCount, String totalQuantity, String totalCost,
                              String lowestUnitPrice, String highestUnitPrice, List<ItemPurchaseRow> rows) {}

    @Transactional(readOnly = true)
    public SupplierHistory supplier(AuthUser caller, UUID supplierId, LocalDate from, LocalDate to) {
        require(caller);
        Supplier s = suppliers.findByBusinessIdAndId(caller.businessId(), supplierId)
                .orElseThrow(() -> ApiException.notFound("Supplier"));
        List<Purchase> found = purchases.search(caller.businessId(), null, supplierId, null, from, to).stream()
                .filter(p -> !p.isDraft()).toList();
        List<Purchase> counted = found.stream().filter(Purchase::isCompleted).toList();
        BigDecimal total = counted.stream().map(Purchase::getTotalCost).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SupplierHistory(s.getId(), s.getName(), counted.size(), Decimals.money(total),
                counted.stream().map(Purchase::getPurchaseDate).min(Comparator.naturalOrder()).orElse(null),
                counted.stream().map(Purchase::getPurchaseDate).max(Comparator.naturalOrder()).orElse(null),
                purchaseService.dtos(caller, found));
    }

    @Transactional(readOnly = true)
    public ItemHistory item(AuthUser caller, UUID itemId, LocalDate from, LocalDate to) {
        require(caller);
        items.findByBusinessIdAndId(caller.businessId(), itemId).orElseThrow(() -> ApiException.notFound("Item"));
        List<PurchaseLine> ls = lines.findByBusinessIdAndItemId(caller.businessId(), itemId);
        Map<UUID, Purchase> ps = ReceivingService.index(purchases.findAllById(
                ls.stream().map(PurchaseLine::getPurchaseId).collect(Collectors.toSet())), Purchase::getId);
        List<PurchaseLine> kept = ls.stream().filter(l -> {
            Purchase p = ps.get(l.getPurchaseId());
            return p != null && p.getBusinessId().equals(caller.businessId()) && !p.isDeleted() && !p.isDraft()
                    && (from == null || !p.getPurchaseDate().isBefore(from)) && (to == null || !p.getPurchaseDate().isAfter(to));
        }).sorted(Comparator.comparing((PurchaseLine l) -> ps.get(l.getPurchaseId()).getPurchaseDate()).reversed()
                .thenComparing(l -> ps.get(l.getPurchaseId()).getCreatedAt(), Comparator.reverseOrder())).toList();
        Map<UUID, BigDecimal> received = receiving.receivedByPurchaseLine(caller.businessId(),
                kept.stream().map(PurchaseLine::getId).toList());
        Map<UUID, String> names = new HashMap<>();
        suppliers.findAllById(ps.values().stream().map(Purchase::getSupplierId).collect(Collectors.toSet()))
                .forEach(s -> names.put(s.getId(), s.getName()));
        List<ItemPurchaseRow> rows = kept.stream().map(l -> {
            Purchase p = ps.get(l.getPurchaseId());
            return new ItemPurchaseRow(p.getId(), l.getId(), p.getPurchaseDate(), p.getStatus(), p.getSupplierId(),
                    names.get(p.getSupplierId()), p.getInvoiceNo(), l.getItemNameSnapshot(), Decimals.format(l.getQuantity()),
                    l.getUnitCodeSnapshot(), l.getPriceBasis(), Decimals.format(l.getEnteredAmount()),
                    Decimals.format(l.getPurchaseUnitPrice()), Decimals.format(l.getSellingUnitPrice()),
                    Decimals.money(l.getTotalCost()), Decimals.format(received.getOrDefault(l.getId(), BigDecimal.ZERO)),
                    p.getCreatedBy(), p.getUpdatedBy());
        }).toList();
        List<PurchaseLine> counted = kept.stream().filter(l -> ps.get(l.getPurchaseId()).isCompleted()).toList();
        // Quantities can only be added up within one unit: the total is given when every counted line shares it.
        boolean oneUnit = counted.stream().map(PurchaseLine::getUnitId).distinct().count() <= 1;
        BigDecimal qty = counted.stream().map(PurchaseLine::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = counted.stream().map(PurchaseLine::getTotalCost).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var prices = counted.stream().map(PurchaseLine::getPurchaseUnitPrice).filter(java.util.Objects::nonNull).toList();
        return new ItemHistory(itemId, (int) counted.stream().map(PurchaseLine::getPurchaseId).distinct().count(),
                oneUnit ? Decimals.format(qty) : null, Decimals.money(cost),
                prices.isEmpty() ? null : Decimals.format(prices.stream().min(Comparator.naturalOrder()).get()),
                prices.isEmpty() ? null : Decimals.format(prices.stream().max(Comparator.naturalOrder()).get()), rows);
    }

    private static void require(AuthUser caller) {
        if (!PurchaseService.canView(caller)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission to view purchase history");
        }
    }
}
