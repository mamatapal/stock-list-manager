package app.stocklist.platformadmin;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AdminSessionRepository extends JpaRepository<AdminSession, UUID> {
    Optional<AdminSession> findBySessionHash(String sessionHash);

    @Modifying
    @Query("update AdminSession s set s.revokedAt = :now where s.adminId = :adminId and s.revokedAt is null")
    int revokeAllForAdmin(UUID adminId, Instant now);
}
