package app.stocklist.network;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, UUID> {
    Optional<SupplierProduct> findBySupplierOrgIdAndId(UUID supplierOrgId, UUID id);

    List<SupplierProduct> findBySupplierOrgIdOrderByNameEnAsc(UUID supplierOrgId);

    List<SupplierProduct> findBySupplierOrgIdAndActiveTrueOrderByNameEnAsc(UUID supplierOrgId);
}
