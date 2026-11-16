package app.stocklist.supplieraccount;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SupplierEmailTokenRepository extends JpaRepository<SupplierEmailToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupplierEmailToken t where t.tokenHash = :hash")
    Optional<SupplierEmailToken> findForUpdate(String hash);

    Optional<SupplierEmailToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, String purpose);

    /** A new link replaces the older ones: they stop working. */
    @Modifying
    @Query("update SupplierEmailToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose "
            + "and t.usedAt is null")
    int retireOpen(UUID userId, String purpose, Instant now);
}
