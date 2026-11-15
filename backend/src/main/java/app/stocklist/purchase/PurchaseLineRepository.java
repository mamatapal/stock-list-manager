package app.stocklist.purchase;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseLineRepository extends JpaRepository<PurchaseLine, UUID> {
    List<PurchaseLine> findByBusinessIdAndPurchaseIdOrderBySortOrderAsc(UUID businessId, UUID purchaseId);

    List<PurchaseLine> findByBusinessIdAndPurchaseIdInOrderBySortOrderAsc(UUID businessId, Collection<UUID> ids);

    List<PurchaseLine> findByBusinessIdAndItemId(UUID businessId, UUID itemId);

    List<PurchaseLine> findByBusinessIdAndStockListItemIdIn(UUID businessId, Collection<UUID> listLineIds);
}
