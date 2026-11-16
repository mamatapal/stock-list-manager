package app.stocklist.supplieraccount;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SupplierRefreshTokenRepository extends JpaRepository<SupplierRefreshToken, UUID> {
    /** Row lock so two simultaneous refreshes of one token cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupplierRefreshToken t where t.tokenHash = :hash")
    Optional<SupplierRefreshToken> findForUpdate(String hash);

    @Modifying
    @Query("update SupplierRefreshToken t set t.revokedAt = :now, t.revokeReason = :reason "
            + "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(UUID familyId, String reason, Instant now);

    @Modifying
    @Query("update SupplierRefreshToken t set t.revokedAt = :now, t.revokeReason = :reason "
            + "where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(UUID userId, String reason, Instant now);
}
