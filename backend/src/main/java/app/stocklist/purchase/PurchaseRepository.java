package app.stocklist.purchase;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    Optional<Purchase> findByBusinessIdAndId(UUID businessId, UUID id);

    /** Serialises everything that changes one purchase (edits, deliveries, cancellation). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.businessId = :businessId and p.id = :id")
    Optional<Purchase> lock(UUID businessId, UUID id);

    /**
     * Whether a purchase was created by the goods receipt of a Supplier Network purchase order
     * (purchase_order.purchase_id). Such a purchase is the order's one final receipt and is never changed afterwards.
     */
    @Query("select count(o) from PurchaseOrder o where o.businessId = :businessId and o.purchaseId = :purchaseId")
    long countOrdersReceivedAs(UUID businessId, UUID purchaseId);

    @Query("select p from Purchase p where p.businessId = :businessId and p.deletedAt is null "
            + "and (:supplierId is null or p.supplierId = :supplierId) "
            + "and (:status is null or p.status = :status) "
            + "and (:fromDate is null or p.purchaseDate >= :fromDate) and (:toDate is null or p.purchaseDate <= :toDate) "
            + "and (:q is null or lower(p.invoiceNo) like :q or lower(p.notes) like :q "
            + "  or exists (select 1 from Supplier s where s.id = p.supplierId and lower(s.name) like :q) "
            + "  or exists (select 1 from PurchaseLine l where l.purchaseId = p.id and lower(l.itemNameSnapshot) like :q)) "
            + "order by p.purchaseDate desc, p.updatedAt desc")
    List<Purchase> search(UUID businessId, String q, UUID supplierId, String status, LocalDate fromDate,
                          LocalDate toDate);

    @Query("select count(p) from Purchase p where p.businessId = :businessId and p.supplierId = :supplierId "
            + "and p.deletedAt is null and p.status <> 'CANCELLED' and lower(p.invoiceNo) = lower(:invoiceNo) "
            + "and p.id <> :exceptId")
    long countSameInvoice(UUID businessId, UUID supplierId, String invoiceNo, UUID exceptId);

    List<Purchase> findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(UUID businessId, long since,
                                                                             Pageable page);

    /** Receiving state is derived from deliveries, so it changes without a new version (no false conflicts). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Purchase p set p.status = :status, p.receivedDate = :receivedDate, p.overReceived = :over, "
            + "p.changeSeq = :seq where p.businessId = :businessId and p.id = :id")
    int setReceiving(UUID businessId, UUID id, String status, LocalDate receivedDate, boolean over, long seq);
}
