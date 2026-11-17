package app.stocklist.network;

import app.stocklist.supplieraccount.SupplierOrg;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Read-only view of supplier organisations for shop discovery: only APPROVED ones that opted in. */
public interface DirectoryRepository extends Repository<SupplierOrg, UUID> {
    @Query("select o from SupplierOrg o where o.status = 'APPROVED' and o.listedInDirectory = true"
            + " and (:like is null or lower(o.legalName) like :like or lower(o.tradeName) like :like"
            + " or lower(o.city) like :like or lower(o.state) like :like)"
            + " order by o.legalName asc")
    List<SupplierOrg> search(String like, Pageable page);

    @Query("select o from SupplierOrg o where o.id = :id and o.status = 'APPROVED' and o.listedInDirectory = true")
    Optional<SupplierOrg> findListed(UUID id);

    Optional<SupplierOrg> findById(UUID id);
}
