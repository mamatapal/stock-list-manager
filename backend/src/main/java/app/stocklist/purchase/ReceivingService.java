package app.stocklist.purchase;

import app.stocklist.common.sync.ChangeSequencer;
import app.stocklist.stocklist.StockListLine;
import app.stocklist.stocklist.StockListLineRepository;
import app.stocklist.stocklist.StockListRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Received quantities, always computed from delivery rows (receipts plus corrections); requested and billed
 * quantities are never changed by receiving.
 *
 * Out-of-stock list line: received = sum over completed, not-cancelled purchase lines linked to it, counting only
 * lines in the same unit. A linked line in another unit is reported as a unit mismatch and not counted.
 */
@Service
public class ReceivingService {
    private final PurchaseRepository purchases;
    private final PurchaseLineRepository purchaseLines;
    private final DeliveryRepository deliveries;
    private final DeliveryLineRepository deliveryLines;
    private final StockListRepository lists;
    private final StockListLineRepository listLines;
    private final ChangeSequencer seq;

    public ReceivingService(PurchaseRepository purchases, PurchaseLineRepository purchaseLines,
                            DeliveryRepository deliveries, DeliveryLineRepository deliveryLines,
                            StockListRepository lists, StockListLineRepository listLines, ChangeSequencer seq) {
        this.purchases = purchases;
        this.purchaseLines = purchaseLines;
        this.deliveries = deliveries;
        this.deliveryLines = deliveryLines;
        this.lists = lists;
        this.listLines = listLines;
        this.seq = seq;
    }

    public record ListLineProgress(ReceivingCalculator.Progress progress, boolean unitMismatch) {}

    /** Received quantity per purchase line (sum of receipts and corrections). */
    public Map<UUID, BigDecimal> receivedByPurchaseLine(UUID businessId, Collection<UUID> purchaseLineIds) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (purchaseLineIds.isEmpty()) return out;
        for (DeliveryLine d : deliveryLines.findByBusinessIdAndPurchaseItemIdIn(businessId, purchaseLineIds)) {
            out.merge(d.getPurchaseItemId(), d.getQuantityReceived(), BigDecimal::add);
        }
        return out;
    }

    public Map<UUID, ListLineProgress> forListLines(UUID businessId, List<StockListLine> lines) {
        Map<UUID, ListLineProgress> out = new HashMap<>();
        if (lines.isEmpty()) return out;
        List<PurchaseLine> linked = purchaseLines.findByBusinessIdAndStockListItemIdIn(businessId,
                lines.stream().map(StockListLine::getId).toList());
        Set<UUID> purchaseIds = linked.stream().map(PurchaseLine::getPurchaseId).collect(Collectors.toSet());
        Set<UUID> counted = new HashSet<>();
        for (Purchase p : purchases.findAllById(purchaseIds)) {
            if (p.getBusinessId().equals(businessId) && !p.isDeleted() && p.isCompleted()) counted.add(p.getId());
        }
        List<PurchaseLine> live = linked.stream().filter(l -> counted.contains(l.getPurchaseId())).toList();
        Map<UUID, BigDecimal> received = receivedByPurchaseLine(businessId,
                live.stream().map(PurchaseLine::getId).toList());
        Map<UUID, List<PurchaseLine>> byListLine = live.stream()
                .collect(Collectors.groupingBy(PurchaseLine::getStockListItemId));
        for (StockListLine l : lines) {
            BigDecimal sum = BigDecimal.ZERO;
            boolean mismatch = false;
            for (PurchaseLine pl : byListLine.getOrDefault(l.getId(), List.of())) {
                if (pl.getUnitId().equals(l.getUnitId())) {
                    sum = sum.add(received.getOrDefault(pl.getId(), BigDecimal.ZERO));
                } else {
                    mismatch = true;
                }
            }
            out.put(l.getId(), new ListLineProgress(ReceivingCalculator.progress(l.getQuantity(), sum), mismatch));
        }
        return out;
    }

    /**
     * Recomputes a purchase's status, latest received date and over-received flag from its deliveries, then the
     * receiving status of every out-of-stock list it is linked to (now or before an edit: pass those list ids).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refresh(UUID businessId, UUID purchaseId, Collection<UUID> alsoLists) {
        Purchase p = purchases.findByBusinessIdAndId(businessId, purchaseId).orElseThrow();
        List<PurchaseLine> lines = purchaseLines.findByBusinessIdAndPurchaseIdOrderBySortOrderAsc(businessId,
                purchaseId);
        if (p.isCompleted()) {
            Map<UUID, BigDecimal> received = receivedByPurchaseLine(businessId,
                    lines.stream().map(PurchaseLine::getId).toList());
            List<String> statuses = new ArrayList<>();
            boolean over = false;
            for (PurchaseLine l : lines) {
                var pr = ReceivingCalculator.progress(l.getQuantity(), received.get(l.getId()));
                statuses.add(pr.status());
                over |= pr.overBy().signum() > 0;
            }
            String status = switch (ReceivingCalculator.overall(statuses)) {
                case ReceivingCalculator.FULL -> Purchase.RECEIVED;
                case ReceivingCalculator.PARTIAL -> Purchase.PARTIAL;
                default -> Purchase.PENDING;
            };
            LocalDate last = deliveries.findByBusinessIdAndPurchaseIdInOrderByReceivedDateAscCreatedAtAsc(businessId,
                    List.of(purchaseId)).stream().map(Delivery::getReceivedDate).max(LocalDate::compareTo)
                    .orElse(null);
            purchases.setReceiving(businessId, purchaseId, status, last, over, seq.next(businessId));
        }
        Set<UUID> listIds = new HashSet<>(alsoLists);
        if (p.getStockListId() != null) listIds.add(p.getStockListId());
        listIds.addAll(listsOf(businessId, lines));
        for (UUID listId : listIds) refreshList(businessId, listId);
    }

    /** List lines that a purchase line points at (these cannot be removed from their list). */
    public Set<UUID> linkedListLines(UUID businessId, Collection<UUID> listLineIds) {
        if (listLineIds.isEmpty()) return Set.of();
        return purchaseLines.findByBusinessIdAndStockListItemIdIn(businessId, listLineIds).stream()
                .map(PurchaseLine::getStockListItemId).collect(Collectors.toSet());
    }

    /** List ids that the given purchase lines point at. */
    public Set<UUID> listsOf(UUID businessId, List<PurchaseLine> lines) {
        Set<UUID> listLineIds = lines.stream().map(PurchaseLine::getStockListItemId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (listLineIds.isEmpty()) return Set.of();
        return listLines.findAllById(listLineIds).stream().filter(l -> l.getBusinessId().equals(businessId))
                .map(StockListLine::getStockListId).collect(Collectors.toSet());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshList(UUID businessId, UUID listId) {
        if (lists.findByBusinessIdAndId(businessId, listId).isEmpty()) return;
        List<StockListLine> lines = listLines.findByBusinessIdAndStockListIdOrderBySortOrderAsc(businessId, listId);
        Map<UUID, ListLineProgress> progress = forListLines(businessId, lines);
        String overall = ReceivingCalculator.overall(lines.stream()
                .map(l -> progress.get(l.getId()).progress().status()).toList());
        lists.setReceiving(businessId, listId, overall, seq.next(businessId));
    }

    static <T> Map<UUID, T> index(Collection<T> rows, Function<T, UUID> key) {
        Map<UUID, T> m = new HashMap<>();
        for (T r : rows) m.put(key.apply(r), r);
        return m;
    }
}
