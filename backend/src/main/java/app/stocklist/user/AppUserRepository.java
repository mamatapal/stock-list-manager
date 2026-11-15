package app.stocklist.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every lookup except login-by-username is scoped to a business. */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<AppUser> findByBusinessIdAndId(UUID businessId, UUID id);

    List<AppUser> findByBusinessIdAndRoleOrderByFullNameAsc(UUID businessId, Role role);

    Optional<AppUser> findByBusinessIdAndRole(UUID businessId, Role role);

    List<AppUser> findByBusinessIdOrderByFullNameAsc(UUID businessId);
}
