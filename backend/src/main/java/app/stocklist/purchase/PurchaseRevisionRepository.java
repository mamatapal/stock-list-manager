package app.stocklist.purchase;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRevisionRepository extends JpaRepository<PurchaseRevision, UUID> {
    List<PurchaseRevision> findByBusinessIdAndPurchaseIdOrderByRevisionNoAsc(UUID businessId, UUID purchaseId);
}
