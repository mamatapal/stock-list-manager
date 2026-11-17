package app.stocklist.network;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Insert and read only: connection history is never changed or deleted. */
public interface SupplierConnectionEventRepository extends Repository<SupplierConnectionEvent, Long> {
    SupplierConnectionEvent save(SupplierConnectionEvent event);

    List<SupplierConnectionEvent> findByConnectionIdOrderByIdAsc(UUID connectionId);
}
