package app.stocklist.network;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Insert and read only: order history is never changed or deleted. */
public interface PurchaseOrderEventRepository extends Repository<PurchaseOrderEvent, Long> {
    PurchaseOrderEvent save(PurchaseOrderEvent event);

    List<PurchaseOrderEvent> findByOrderIdOrderByIdAsc(UUID orderId);
}
