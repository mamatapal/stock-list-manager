package app.stocklist.network;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierProductLinkRepository extends JpaRepository<SupplierProductLink, UUID> {
    Optional<SupplierProductLink> findByBusinessIdAndSupplierProductId(UUID businessId, UUID supplierProductId);

    List<SupplierProductLink> findByBusinessIdAndSupplierOrgId(UUID businessId, UUID supplierOrgId);
}
