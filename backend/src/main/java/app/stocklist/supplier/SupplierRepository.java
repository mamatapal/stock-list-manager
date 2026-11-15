package app.stocklist.supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByBusinessIdAndId(UUID businessId, UUID id);

    @Query("select s from Supplier s where s.businessId = :businessId and s.deletedAt is null "
            + "and (:includeInactive = true or s.active = true) "
            + "and (:q is null or lower(s.name) like :q or lower(s.contactPerson) like :q or s.mobile like :q "
            + "or lower(s.city) like :q) order by lower(s.name)")
    List<Supplier> search(UUID businessId, String q, boolean includeInactive);

    List<Supplier> findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(UUID businessId, long since,
                                                                             Pageable page);
}
