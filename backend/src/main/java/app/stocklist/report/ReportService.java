package app.stocklist.report;

import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.Decimals;
import app.stocklist.permission.Permission;
import app.stocklist.purchase.Purchase;
import app.stocklist.purchase.PurchaseLine;
import app.stocklist.purchase.PurchaseLineRepository;
import app.stocklist.purchase.PurchaseRepository;
import app.stocklist.supplier.SupplierRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business reports, worked out on the server from the purchase snapshots so they cover every purchase and every
 * phone. Only completed purchases (not drafts, not cancelled) are counted.
 *
 * <p>Permissions are checked here, not on the phone:
 * <ul>
 *   <li>purchase report, supplier totals, item prices: PURCHASE_HISTORY_VIEW;</li>
 *   <li>expected-profit figures inside them: also EXPECTED_PROFIT_VIEW (otherwise the fields are null);</li>
 *   <li>the expected-profit report: PROFIT_REPORT_VIEW and EXPECTED_PROFIT_VIEW.</li>
 * </ul>
 * Expected profit is always an estimate from the selling prices entered on the purchase lines. It is never realised
 * profit: nothing here knows what was actually sold.
 */
@Service
public class ReportService {
    public static final String EXPECTED_PROFIT_NOTE = "Expected profit is an estimate: selling price minus purchase "
            + "cost, for lines with a selling price. It is not realised profit; actual sales are not recorded.";
    private static final int MAX_DAYS = 3660;

    private final PurchaseRepository purchases;
    private final PurchaseLineRepository lines;
    private final SupplierRepository suppliers;

    public ReportService(PurchaseRepository purchases, PurchaseLineRepository lines, SupplierRepository suppliers) {
        this.purchases = purchases;
        this.lines = lines;
        this.suppliers = suppliers;
    }

    // ---------------------------------------------------------------------------------------------- DTOs

    public record Period(LocalDate from, LocalDate to) {}

    public record PurchaseRow(UUID purchaseId, LocalDate purchaseDate, String invoiceNo, UUID supplierId,
                              String supplierName, String status, int itemCount, String totalCost,
                              String expectedSales, String expectedProfit) {}

    public record PurchaseReport(Period period, int purchaseCount, int cancelledCount, String totalCost,
                                 String expectedSales, String expectedProfit, boolean profitIncluded,
                                 String profitNote, List<PurchaseRow> rows) {}

    public record SupplierTotal(UUID supplierId, String supplierName, int purchaseCount, String totalCost,
                                String sharePercent, LocalDate lastPurchaseDate, String expectedProfit) {}

    public record SupplierTotals(Period period, int purchaseCount, String totalCost, boolean profitIncluded,
                                 String profitNote, List<SupplierTotal> rows) {}

    public record ItemPrice(UUID itemId, String itemName, String unitCode, int purchaseCount, String totalQuantity,
                            String totalCost, String lowestUnitPrice, String highestUnitPrice,
                            String averageUnitPrice, String latestUnitPrice, LocalDate latestDate,
                            String latestSupplierName, String latestSellingPrice, String expectedProfit) {}

    public record ItemPrices(Period period, boolean profitIncluded, String profitNote, List<ItemPrice> rows) {}

    public record ProfitRow(UUID id, String name, String cost, String expectedSales, String expectedProfit,
                            String marginPercent) {}

    public record ExpectedProfitReport(Period period, int purchaseCount, String totalCost, String pricedCost,
                                       String unpricedCost, String expectedSales, String expectedProfit,
                                       String marginPercent, String note, List<ProfitRow> bySupplier,
                                       List<ProfitRow> byItem) {}

    // ---------------------------------------------------------------------------------------------- reports

    @Transactional(readOnly = true)
    public PurchaseReport purchases(AuthUser caller, LocalDate from, LocalDate to, UUID supplierId, UUID itemId) {
        requireHistory(caller);
        check(from, to);
        boolean profit = caller.has(Permission.EXPECTED_PROFIT_VIEW);
        List<Purchase> found = purchases.search(caller.businessId(), null, supplierId, null, from, to).stream()
                .filter(p -> !p.isDraft()).toList();
        if (itemId != null) {
            var withItem = lines.findByBusinessIdAndItemId(caller.businessId(), itemId).stream()
                    .map(PurchaseLine::getPurchaseId).collect(Collectors.toSet());
            found = found.stream().filter(p -> withItem.contains(p.getId())).toList();
        }
        List<Purchase> counted = found.stream().filter(Purchase::isCompleted).toList();
        Map<UUID, String> names = supplierNames(found);
        List<PurchaseRow> rows = found.stream().sorted(Comparator.comparing(Purchase::getPurchaseDate)
                        .thenComparing(Purchase::getCreatedAt))
                .map(p -> new PurchaseRow(p.getId(), p.getPurchaseDate(), p.getInvoiceNo(), p.getSupplierId(),
                        names.get(p.getSupplierId()), p.getStatus(), p.getItemCount(), Decimals.money(p.getTotalCost()),
                        profit ? Decimals.money(p.getTotalExpectedSales()) : null,
                        profit ? Decimals.money(p.getTotalExpectedProfit()) : null))
                .toList();
        return new PurchaseReport(new Period(from, to), counted.size(), found.size() - counted.size(),
                Decimals.money(sum(counted, Purchase::getTotalCost)),
                profit ? Decimals.money(sum(counted, Purchase::getTotalExpectedSales)) : null,
                profit ? Decimals.money(sum(counted, Purchase::getTotalExpectedProfit)) : null,
                profit, profit ? EXPECTED_PROFIT_NOTE : null, rows);
    }

    @Transactional(readOnly = true)
    public SupplierTotals supplierTotals(AuthUser caller, LocalDate from, LocalDate to) {
        requireHistory(caller);
        check(from, to);
        boolean profit = caller.has(Permission.EXPECTED_PROFIT_VIEW);
        List<Purchase> counted = completed(caller, from, to, null);
        Map<UUID, String> names = supplierNames(counted);
        BigDecimal grand = sum(counted, Purchase::getTotalCost);
        Map<UUID, List<Purchase>> bySupplier = counted.stream()
                .collect(Collectors.groupingBy(Purchase::getSupplierId, LinkedHashMap::new, Collectors.toList()));
        List<SupplierTotal> rows = bySupplier.entrySet().stream().map(e -> {
            BigDecimal cost = sum(e.getValue(), Purchase::getTotalCost);
            return new SupplierTotal(e.getKey(), names.get(e.getKey()), e.getValue().size(), Decimals.money(cost),
                    percent(cost, grand),
                    e.getValue().stream().map(Purchase::getPurchaseDate).max(Comparator.naturalOrder()).orElse(null),
                    profit ? Decimals.money(sum(e.getValue(), Purchase::getTotalExpectedProfit)) : null);
        }).sorted(Comparator.comparing((SupplierTotal r) -> new BigDecimal(r.totalCost())).reversed()
                .thenComparing(r -> r.supplierName() == null ? "" : r.supplierName().toLowerCase())).toList();
        return new SupplierTotals(new Period(from, to), counted.size(), Decimals.money(grand), profit,
                profit ? EXPECTED_PROFIT_NOTE : null, rows);
    }

    @Transactional(readOnly = true)
    public ItemPrices itemPrices(AuthUser caller, LocalDate from, LocalDate to, UUID supplierId) {
        requireHistory(caller);
        check(from, to);
        boolean profit = caller.has(Permission.EXPECTED_PROFIT_VIEW);
        List<Purchase> counted = completed(caller, from, to, supplierId);
        Map<UUID, Purchase> byId = counted.stream().collect(Collectors.toMap(Purchase::getId, Function.identity()));
        Map<UUID, String> names = supplierNames(counted);
        List<PurchaseLine> all = byId.isEmpty() ? List.of()
                : lines.findByBusinessIdAndPurchaseIdInOrderBySortOrderAsc(caller.businessId(), byId.keySet());
        // Prices are compared within one unit: the same item bought in boxes and in pieces gives two rows.
        Map<String, List<PurchaseLine>> groups = all.stream().collect(Collectors.groupingBy(
                l -> l.getItemId() + "/" + l.getUnitId(), LinkedHashMap::new, Collectors.toList()));
        Comparator<PurchaseLine> newest = Comparator.comparing((PurchaseLine l) -> byId.get(l.getPurchaseId()).getPurchaseDate())
                .thenComparing(l -> byId.get(l.getPurchaseId()).getCreatedAt());
        List<ItemPrice> rows = new ArrayList<>();
        for (List<PurchaseLine> g : groups.values()) {
            PurchaseLine latest = g.stream().max(newest).orElseThrow();
            List<BigDecimal> prices = g.stream().map(PurchaseLine::getPurchaseUnitPrice).filter(Objects::nonNull).toList();
            List<PurchaseLine> priced = g.stream().filter(l -> l.getTotalCost() != null).toList();
            BigDecimal pricedQty = priced.stream().map(PurchaseLine::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pricedCost = priced.stream().map(PurchaseLine::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            PurchaseLine latestPriced = g.stream().filter(l -> l.getPurchaseUnitPrice() != null).max(newest).orElse(null);
            PurchaseLine latestSelling = g.stream().filter(l -> l.getSellingUnitPrice() != null).max(newest).orElse(null);
            Purchase lp = byId.get(latest.getPurchaseId());
            rows.add(new ItemPrice(latest.getItemId(), latest.getItemNameSnapshot(), latest.getUnitCodeSnapshot(),
                    (int) g.stream().map(PurchaseLine::getPurchaseId).distinct().count(),
                    Decimals.format(g.stream().map(PurchaseLine::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add)),
                    Decimals.money(pricedCost),
                    prices.isEmpty() ? null : price(prices.stream().min(Comparator.naturalOrder()).get()),
                    prices.isEmpty() ? null : price(prices.stream().max(Comparator.naturalOrder()).get()),
                    pricedQty.signum() == 0 ? null : price(pricedCost.divide(pricedQty, 4, RoundingMode.HALF_UP)),
                    latestPriced == null ? null : price(latestPriced.getPurchaseUnitPrice()),
                    lp.getPurchaseDate(), names.get(lp.getSupplierId()),
                    latestSelling == null ? null : price(latestSelling.getSellingUnitPrice()),
                    profit ? Decimals.money(g.stream().map(PurchaseLine::getExpectedProfit).filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)) : null));
        }
        rows.sort(Comparator.comparing((ItemPrice r) -> r.itemName().toLowerCase()).thenComparing(ItemPrice::unitCode));
        return new ItemPrices(new Period(from, to), profit, profit ? EXPECTED_PROFIT_NOTE : null, rows);
    }

    @Transactional(readOnly = true)
    public ExpectedProfitReport expectedProfit(AuthUser caller, LocalDate from, LocalDate to) {
        if (!caller.has(Permission.PROFIT_REPORT_VIEW) || !caller.has(Permission.EXPECTED_PROFIT_VIEW)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission to view profit reports");
        }
        check(from, to);
        List<Purchase> counted = completed(caller, from, to, null);
        Map<UUID, Purchase> byId = counted.stream().collect(Collectors.toMap(Purchase::getId, Function.identity()));
        Map<UUID, String> names = supplierNames(counted);
        List<PurchaseLine> all = byId.isEmpty() ? List.of()
                : lines.findByBusinessIdAndPurchaseIdInOrderBySortOrderAsc(caller.businessId(), byId.keySet());
        Totals total = Totals.of(all);
        List<ProfitRow> bySupplier = group(all, l -> byId.get(l.getPurchaseId()).getSupplierId(),
                id -> names.get(id));
        Map<UUID, String> itemNames = new HashMap<>();
        all.forEach(l -> itemNames.putIfAbsent(l.getItemId(), l.getItemNameSnapshot()));
        List<ProfitRow> byItem = group(all, PurchaseLine::getItemId, itemNames::get);
        return new ExpectedProfitReport(new Period(from, to), counted.size(), Decimals.money(total.cost),
                Decimals.money(total.pricedCost), Decimals.money(total.cost.subtract(total.pricedCost)),
                Decimals.money(total.sales), Decimals.money(total.profit), percent(total.profit, total.sales),
                EXPECTED_PROFIT_NOTE, bySupplier, byItem);
    }

    // ---------------------------------------------------------------------------------------------- helpers

    /** Totals over lines; profit and sales only from lines that have a selling price. */
    private record Totals(BigDecimal cost, BigDecimal pricedCost, BigDecimal sales, BigDecimal profit) {
        static Totals of(List<PurchaseLine> ls) {
            BigDecimal cost = BigDecimal.ZERO, pricedCost = BigDecimal.ZERO, sales = BigDecimal.ZERO, profit = BigDecimal.ZERO;
            for (PurchaseLine l : ls) {
                if (l.getTotalCost() != null) cost = cost.add(l.getTotalCost());
                if (l.getExpectedSales() != null && l.getTotalCost() != null) {
                    pricedCost = pricedCost.add(l.getTotalCost());
                    sales = sales.add(l.getExpectedSales());
                    profit = profit.add(l.getExpectedProfit() == null ? BigDecimal.ZERO : l.getExpectedProfit());
                }
            }
            return new Totals(cost, pricedCost, sales, profit);
        }
    }

    private static List<ProfitRow> group(List<PurchaseLine> all, Function<PurchaseLine, UUID> key,
                                         Function<UUID, String> name) {
        return all.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream().map(e -> {
                    Totals t = Totals.of(e.getValue());
                    return new ProfitRow(e.getKey(), name.apply(e.getKey()), Decimals.money(t.cost),
                            Decimals.money(t.sales), Decimals.money(t.profit), percent(t.profit, t.sales));
                })
                .sorted(Comparator.comparing((ProfitRow r) -> new BigDecimal(r.expectedProfit())).reversed()
                        .thenComparing(r -> r.name() == null ? "" : r.name().toLowerCase()))
                .toList();
    }

    private List<Purchase> completed(AuthUser caller, LocalDate from, LocalDate to, UUID supplierId) {
        return purchases.search(caller.businessId(), null, supplierId, null, from, to).stream()
                .filter(Purchase::isCompleted).toList();
    }

    private Map<UUID, String> supplierNames(List<Purchase> ps) {
        Map<UUID, String> names = new HashMap<>();
        suppliers.findAllById(ps.stream().map(Purchase::getSupplierId).collect(Collectors.toSet()))
                .forEach(s -> names.put(s.getId(), s.getName()));
        return names;
    }

    private static BigDecimal sum(List<Purchase> ps, Function<Purchase, BigDecimal> f) {
        return ps.stream().map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** part / whole as a percentage with 2 decimals; null when whole is zero. */
    static String percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return null;
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Unit prices keep up to 4 decimals but always show at least 2 (12.5 -> "12.50", 0.3333 -> "0.3333"). */
    static String price(BigDecimal p) {
        BigDecimal s = p.stripTrailingZeros();
        return (s.scale() < 2 ? s.setScale(2, RoundingMode.UNNECESSARY) : s.setScale(Math.min(s.scale(), 4), RoundingMode.HALF_UP))
                .toPlainString();
    }

    private static void requireHistory(AuthUser caller) {
        if (!caller.has(Permission.PURCHASE_HISTORY_VIEW)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission to view purchase reports");
        }
    }

    private static void check(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("INVALID_PERIOD", "Choose a start and end date");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_PERIOD", "The end date is before the start date");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw ApiException.badRequest("INVALID_PERIOD", "Choose a period of at most 10 years");
        }
    }
}
