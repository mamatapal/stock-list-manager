package app.stocklist.network;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SupplierConnectionRepository extends JpaRepository<SupplierConnection, UUID> {
    Optional<SupplierConnection> findByBusinessIdAndId(UUID businessId, UUID id);

    Optional<SupplierConnection> findBySupplierOrgIdAndId(UUID supplierOrgId, UUID id);

    Optional<SupplierConnection> findByBusinessIdAndSupplierOrgId(UUID businessId, UUID supplierOrgId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SupplierConnection c where c.businessId = :businessId and c.id = :id")
    Optional<SupplierConnection> lockForShop(UUID businessId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SupplierConnection c where c.supplierOrgId = :orgId and c.id = :id")
    Optional<SupplierConnection> lockForSupplier(UUID orgId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SupplierConnection c where c.businessId = :businessId and c.supplierOrgId = :orgId")
    Optional<SupplierConnection> lockPair(UUID businessId, UUID orgId);

    List<SupplierConnection> findByBusinessIdOrderByUpdatedAtDesc(UUID businessId);

    List<SupplierConnection> findBySupplierOrgIdOrderByUpdatedAtDesc(UUID supplierOrgId);

    List<SupplierConnection> findBySupplierOrgIdAndStatusOrderByUpdatedAtDesc(UUID supplierOrgId, String status);

    boolean existsByBusinessIdAndSupplierIdAndIdNot(UUID businessId, UUID supplierId, UUID id);
}
