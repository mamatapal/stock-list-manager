package app.stocklist.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Row lock so two simultaneous refreshes of one token cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findForUpdate(String hash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason "
            + "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(UUID familyId, String reason, Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason "
            + "where t.businessId = :businessId and t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(UUID businessId, UUID userId, String reason, Instant now);

    @Query("select t from RefreshToken t where t.businessId = :businessId and t.userId = :userId "
            + "and t.revokedAt is null and t.expiresAt > :now order by t.issuedAt desc")
    List<RefreshToken> findActive(UUID businessId, UUID userId, Instant now);
}
