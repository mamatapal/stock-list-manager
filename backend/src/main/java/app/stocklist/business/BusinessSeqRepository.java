package app.stocklist.business;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BusinessSeqRepository extends JpaRepository<BusinessSeq, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BusinessSeq s where s.businessId = :businessId")
    Optional<BusinessSeq> lockById(UUID businessId);
}
