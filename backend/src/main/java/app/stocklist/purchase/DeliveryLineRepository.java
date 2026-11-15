package app.stocklist.purchase;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryLineRepository extends JpaRepository<DeliveryLine, UUID> {
    List<DeliveryLine> findByBusinessIdAndDeliveryIdIn(UUID businessId, Collection<UUID> deliveryIds);

    List<DeliveryLine> findByBusinessIdAndPurchaseItemIdIn(UUID businessId, Collection<UUID> purchaseLineIds);
}
