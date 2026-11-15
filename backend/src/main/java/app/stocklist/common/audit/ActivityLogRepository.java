package app.stocklist.common.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Deliberately not a CrudRepository: no update or delete methods exist. */
public interface ActivityLogRepository extends Repository<ActivityLog, Long> {
    ActivityLog save(ActivityLog log);

    Page<ActivityLog> findByBusinessIdOrderByCreatedAtDescIdDesc(UUID businessId, Pageable pageable);

    List<ActivityLog> findByBusinessIdAndAction(UUID businessId, String action);
}
