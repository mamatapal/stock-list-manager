package app.stocklist.supplieraccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierUserRepository extends JpaRepository<SupplierUser, UUID> {
    Optional<SupplierUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<SupplierUser> findBySupplierOrgId(UUID supplierOrgId);
}
