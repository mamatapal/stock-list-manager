package app.stocklist.sync;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncOperationRepository extends JpaRepository<SyncOperation, Long> {
    Optional<SyncOperation> findByBusinessIdAndOpId(UUID businessId, UUID opId);
}
