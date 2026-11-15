package app.stocklist.purchase;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BatchRepository extends JpaRepository<Batch, UUID> {
    Optional<Batch> findByBusinessIdAndDeliveryItemId(UUID businessId, UUID deliveryItemId);

    Optional<Batch> findByBusinessIdAndId(UUID businessId, UUID id);

    List<Batch> findByBusinessIdAndPurchaseId(UUID businessId, UUID purchaseId);

    /**
     * Batch tracking and expiry lists. Draft and deleted purchases never have batches; cancelled purchases are left
     * out unless asked for. expiryFrom/expiryTo bound the expiry date (null = open); withExpiryOnly drops batches
     * without an expiry date (every expiry list); inStockOnly drops batches whose quantity was corrected to zero.
     */
    @Query("select b from Batch b join Purchase p on p.id = b.purchaseId "
            + "where b.businessId = :businessId and p.businessId = :businessId and p.deletedAt is null "
            + "and (:includeCancelled = true or p.status <> 'CANCELLED') "
            + "and (:withExpiryOnly = false or b.expiryDate is not null) "
            + "and (:expiryFrom is null or b.expiryDate >= :expiryFrom) "
            + "and (:expiryTo is null or b.expiryDate <= :expiryTo) "
            + "and (:inStockOnly = false or b.quantity > 0) "
            + "and (:itemId is null or b.itemId = :itemId) "
            + "and (:supplierId is null or p.supplierId = :supplierId) "
            + "and (:receivedFrom is null or b.receivedDate >= :receivedFrom) "
            + "and (:receivedTo is null or b.receivedDate <= :receivedTo) "
            + "and (:q is null or lower(b.batchNo) like :q or lower(p.invoiceNo) like :q "
            + "  or exists (select 1 from PurchaseLine l where l.id = b.purchaseItemId and lower(l.itemNameSnapshot) like :q) "
            + "  or exists (select 1 from Supplier s where s.id = p.supplierId and lower(s.name) like :q)) "
            + "order by case when b.expiryDate is null then 1 else 0 end, b.expiryDate asc, b.receivedDate desc")
    List<Batch> search(UUID businessId, boolean includeCancelled, boolean withExpiryOnly, LocalDate expiryFrom,
                       LocalDate expiryTo, boolean inStockOnly, UUID itemId, UUID supplierId, LocalDate receivedFrom,
                       LocalDate receivedTo, String q);

    List<Batch> findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(UUID businessId, long since,
                                                                          Pageable page);

    /** A purchase's batches show its supplier, invoice and status: phones refetch them when it changes. */
    @Modifying(flushAutomatically = true)
    @Query("update Batch b set b.changeSeq = :seq where b.businessId = :businessId and b.purchaseId = :purchaseId")
    int touchForPurchase(UUID businessId, UUID purchaseId, long seq);
}
