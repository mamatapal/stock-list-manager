package app.stocklist.supplieraccount;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierOrgEventRepository extends JpaRepository<SupplierOrgEvent, Long> {
    List<SupplierOrgEvent> findBySupplierOrgIdOrderByIdAsc(UUID supplierOrgId);
}
