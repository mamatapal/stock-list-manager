package app.stocklist.permission;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermissionId> {

    List<UserPermission> findByBusinessIdAndUserId(UUID businessId, UUID userId);

    @Modifying
    @Query("delete from UserPermission p where p.businessId = :businessId and p.userId = :userId")
    int deleteAllForUser(UUID businessId, UUID userId);

    @Query(value = "select code from permission order by sort_order", nativeQuery = true)
    List<String> catalogueCodes();
}
