package app.stocklist.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    Optional<Item> findByBusinessIdAndId(UUID businessId, UUID id);

    @Query("select i from Item i where i.businessId = :businessId and i.deletedAt is null "
            + "and (:includeInactive = true or i.active = true) "
            + "and (:q is null or lower(i.name) like :q or lower(i.category) like :q) "
            + "and (:category is null or i.category = :category) order by lower(i.name)")
    List<Item> search(UUID businessId, String q, String category, boolean includeInactive);

    @Query("select distinct i.category from Item i where i.businessId = :businessId and i.deletedAt is null "
            + "and i.category is not null order by i.category")
    List<String> categories(UUID businessId);

    @Query("select case when count(i) > 0 then true else false end from Item i where i.businessId = :businessId and i.deletedAt is null "
            + "and lower(i.name) = lower(:name) and i.id <> :exceptId")
    boolean nameTaken(UUID businessId, String name, UUID exceptId);

    List<Item> findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(UUID businessId, long since, Pageable page);
}
