package app.stocklist.network;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findByBusinessIdAndOrderIdOrderByLineNoAsc(UUID businessId, UUID orderId);
}
