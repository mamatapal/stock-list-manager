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

    /** The connection of an order (never changes), read without loading the order, so it can be locked first. */
    @Query("select o.connectionId from PurchaseOrder o where o.businessId = :businessId and o.id = :id")
    Optional<UUID> connectionIdForShop(UUID businessId, UUID id);

    @Query("select o.connectionId from PurchaseOrder o where o.supplierOrgId = :orgId and o.id = :id")
    Optional<UUID> connectionIdForSupplier(UUID orgId, UUID id);

    /** Open orders of a connection, locked: a locking read always sees the latest committed rows. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.businessId = :businessId and o.connectionId = :connectionId"
            + " and o.status in :statuses order by o.id")
    List<PurchaseOrder> lockOpenForConnection(UUID businessId, UUID connectionId,
                                              java.util.Collection<String> statuses);

    @Query("select o from PurchaseOrder o where o.businessId = :businessId and (:status is null or o.status = :status)"
            + " order by o.createdAt desc")
    List<PurchaseOrder> searchForShop(UUID businessId, String status);

    @Query("select o from PurchaseOrder o where o.supplierOrgId = :orgId and (:status is null or o.status = :status)"
            + " order by o.createdAt desc")
    List<PurchaseOrder> searchForSupplier(UUID orgId, String status);

}
