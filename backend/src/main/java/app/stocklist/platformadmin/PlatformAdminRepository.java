package app.stocklist.platformadmin;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {
    /** Row lock so the same authenticator code cannot be accepted by two simultaneous sign-ins. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PlatformAdmin a where a.username = :username")
    Optional<PlatformAdmin> findForUpdate(String username);

    Optional<PlatformAdmin> findByUsername(String username);
}
