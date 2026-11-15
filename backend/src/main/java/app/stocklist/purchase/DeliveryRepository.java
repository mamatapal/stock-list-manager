package app.stocklist.purchase;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    Optional<Delivery> findByBusinessIdAndId(UUID businessId, UUID id);

    List<Delivery> findByBusinessIdAndPurchaseIdInOrderByReceivedDateAscCreatedAtAsc(UUID businessId,
                                                                                    Collection<UUID> purchaseIds);
}
