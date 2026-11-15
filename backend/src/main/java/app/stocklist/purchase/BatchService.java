package app.stocklist.purchase;

import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.Decimals;
import app.stocklist.permission.Permission;
import app.stocklist.supplier.Supplier;
import app.stocklist.supplier.SupplierRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch tracking and expiry lists (EXPIRY_VIEW). Every batch links back to the purchase, purchase line and
 * delivery it came from; item name and unit are the purchase line's snapshots, so they never change later.
 */
@Service
public class BatchService {
    private final BatchRepository batches;
    private final PurchaseRepository purchases;
    private final PurchaseLineRepository lines;
    private final DeliveryLineRepository deliveryLines;
    private final DeliveryRepository deliveries;
    private final SupplierRepository suppliers;
    private final Clock clock;
    private final ZoneId zone;

    public BatchService(BatchRepository batches, PurchaseRepository purchases, PurchaseLineRepository lines,
                        DeliveryLineRepository deliveryLines, DeliveryRepository deliveries,
                        SupplierRepository suppliers, Clock clock,
                        @Value("${app.business-zone:Asia/Kolkata}") String zone) {
        this.batches = batches;
        this.purchases = purchases;
        this.lines = lines;
        this.deliveryLines = deliveryLines;
        this.deliveries = deliveries;
        this.suppliers = suppliers;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    /**
     * quantity: what is left of this batch (receipt plus corrections). receivedQuantity: the original receipt.
     * daysToExpiry is counted from the server's today (the phone recalculates with its own date).
     */
    public record BatchDto(UUID id, UUID itemId, String itemName, UUID purchaseId, UUID purchaseLineId,
                           UUID deliveryId, UUID supplierId, String supplierName, String invoiceNo,
                           LocalDate purchaseDate, String purchaseStatus, LocalDate receivedDate, UUID receivedBy,
                           String batchNo, String receivedQuantity, String quantity, UUID unitId, String unitCode,
                           LocalDate expiryDate, Long daysToExpiry, String expiryLabel, boolean cancelled) {}

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    @Transactional(readOnly = true)
    public List<BatchDto> search(AuthUser caller, ExpiryCalculator.Filter expiry, String q, UUID itemId,
                                 UUID supplierId, LocalDate receivedFrom, LocalDate receivedTo,
                                 boolean includeCancelled, LocalDate today) {
        requireView(caller);
        LocalDate day = today == null ? today() : today;
        LocalDate[] range = expiry == null ? new LocalDate[] {null, null} : ExpiryCalculator.range(expiry, day);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        // Expiry lists show only stock that is still there and still counts (not cancelled).
        boolean expiryList = expiry != null;
        return dtos(caller.businessId(), batches.search(caller.businessId(), includeCancelled && !expiryList,
                expiryList, range[0], range[1], expiryList, itemId, supplierId, receivedFrom, receivedTo, like), day);
    }

    @Transactional(readOnly = true)
    public BatchDto get(AuthUser caller, UUID id) {
        requireView(caller);
        Batch b = batches.findByBusinessIdAndId(caller.businessId(), id)
                .orElseThrow(() -> ApiException.notFound("Batch"));
        return dtos(caller.businessId(), List.of(b), today()).get(0);
    }

    /** Counts for the dashboard alert: expired, and due within 7 days (in stock, not cancelled). */
    public record ExpiryAlert(LocalDate today, int expired, int within7, int within30) {}

    @Transactional(readOnly = true)
    public ExpiryAlert alert(AuthUser caller) {
        requireView(caller);
        LocalDate day = today();
        return new ExpiryAlert(day, count(caller, ExpiryCalculator.Filter.EXPIRED, day),
                count(caller, ExpiryCalculator.Filter.WITHIN_7, day), count(caller, ExpiryCalculator.Filter.WITHIN_30, day));
    }

    private int count(AuthUser caller, ExpiryCalculator.Filter f, LocalDate day) {
        LocalDate[] r = ExpiryCalculator.range(f, day);
        return batches.search(caller.businessId(), false, true, r[0], r[1], true, null, null, null, null, null).size();
    }

    public List<BatchDto> dtos(UUID bid, Collection<Batch> found, LocalDate today) {
        if (found.isEmpty()) return List.of();
        Map<UUID, Purchase> ps = ReceivingService.index(purchases.findAllById(
                found.stream().map(Batch::getPurchaseId).collect(Collectors.toSet())), Purchase::getId);
        Map<UUID, PurchaseLine> pls = ReceivingService.index(lines.findAllById(
                found.stream().map(Batch::getPurchaseItemId).collect(Collectors.toSet())), PurchaseLine::getId);
        Map<UUID, DeliveryLine> dls = ReceivingService.index(deliveryLines.findAllById(
                found.stream().map(Batch::getDeliveryItemId).collect(Collectors.toSet())), DeliveryLine::getId);
        Map<UUID, Delivery> ds = ReceivingService.index(deliveries.findAllById(
                dls.values().stream().map(DeliveryLine::getDeliveryId).collect(Collectors.toSet())), Delivery::getId);
        Set<UUID> supplierIds = new HashSet<>();
        ps.values().forEach(p -> supplierIds.add(p.getSupplierId()));
        Map<UUID, String> names = new HashMap<>();
        for (Supplier s : suppliers.findAllById(supplierIds)) {
            if (s.getBusinessId().equals(bid)) names.put(s.getId(), s.getName());
        }
        return found.stream().filter(b -> b.getBusinessId().equals(bid)).map(b -> {
            Purchase p = ps.get(b.getPurchaseId());
            PurchaseLine l = pls.get(b.getPurchaseItemId());
            DeliveryLine dl = dls.get(b.getDeliveryItemId());
            Delivery d = dl == null ? null : ds.get(dl.getDeliveryId());
            return new BatchDto(b.getId(), b.getItemId(), l == null ? null : l.getItemNameSnapshot(), b.getPurchaseId(),
                    b.getPurchaseItemId(), d == null ? null : d.getId(), p.getSupplierId(), names.get(p.getSupplierId()),
                    p.getInvoiceNo(), p.getPurchaseDate(), p.getStatus(), b.getReceivedDate(),
                    d == null ? null : d.getReceivedBy(), b.getBatchNo(),
                    dl == null ? null : Decimals.format(dl.getQuantityReceived()), Decimals.format(b.getQuantity()),
                    b.getUnitId(), l == null ? null : l.getUnitCodeSnapshot(), b.getExpiryDate(),
                    ExpiryCalculator.daysLeft(b.getExpiryDate(), today), ExpiryCalculator.label(b.getExpiryDate(), today),
                    p.isCancelled());
        }).toList();
    }

    private static void requireView(AuthUser caller) {
        if (!caller.has(Permission.EXPIRY_VIEW)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission to view batches and expiry");
        }
    }
}
