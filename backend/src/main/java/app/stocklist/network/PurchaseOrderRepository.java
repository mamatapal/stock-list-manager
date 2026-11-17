package app.stocklist.network;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByBusinessIdAndId(UUID businessId, UUID id);

    Optional<PurchaseOrder> findBySupplierOrgIdAndId(UUID supplierOrgId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.businessId = :businessId and o.id = :id")
    Optional<PurchaseOrder> lockForShop(UUID businessId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.supplierOrgId = :orgId and o.id = :id")
    Optional<PurchaseOrder> lockForSupplier(UUID orgId, UUID id);

    @Query("select o from PurchaseOrder o where o.businessId = :businessId and (:status is null or o.status = :status)"
            + " order by o.createdAt desc")
    List<PurchaseOrder> searchForShop(UUID businessId, String status);

    @Query("select o from PurchaseOrder o where o.supplierOrgId = :orgId and (:status is null or o.status = :status)"
            + " order by o.createdAt desc")
    List<PurchaseOrder> searchForSupplier(UUID orgId, String status);

    List<PurchaseOrder> findByBusinessIdAndConnectionIdAndStatusIn(UUID businessId, UUID connectionId,
                                                                   java.util.Collection<String> statuses);
}
