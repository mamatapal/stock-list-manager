package app.stocklist.supplieraccount;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SupplierOrgRepository extends JpaRepository<SupplierOrg, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SupplierOrg o where o.id = :id")
    Optional<SupplierOrg> findForUpdate(UUID id);

    List<SupplierOrg> findByStatusOrderBySubmittedAtAscCreatedAtAsc(String status);

    List<SupplierOrg> findAllByOrderByCreatedAtDesc();

    @Query("select count(o) from SupplierOrg o where o.id <> :id and o.gstin = :gstin")
    long countOtherWithGstin(UUID id, String gstin);

    @Query("select count(o) from SupplierOrg o where o.id <> :id and o.mobile = :mobile")
    long countOtherWithMobile(UUID id, String mobile);
}
