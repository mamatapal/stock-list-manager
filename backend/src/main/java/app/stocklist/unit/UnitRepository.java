package app.stocklist.unit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UnitRepository extends JpaRepository<UnitOfMeasure, UUID> {

    /** Built-in units plus this business's live custom units. */
    @Query("select u from UnitOfMeasure u where (u.businessId is null or u.businessId = :businessId) "
            + "and u.deletedAt is null order by u.businessId nulls first, u.code")
    List<UnitOfMeasure> usable(UUID businessId);

    /** A unit this business may use: built-in, or its own and not deleted. */
    @Query("select u from UnitOfMeasure u where u.id = :id and (u.businessId is null or u.businessId = :businessId)")
    Optional<UnitOfMeasure> findUsable(UUID businessId, UUID id);

    Optional<UnitOfMeasure> findByBusinessIdAndId(UUID businessId, UUID id);

    List<UnitOfMeasure> findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(UUID businessId, long since,
                                                                                  Pageable page);
}
