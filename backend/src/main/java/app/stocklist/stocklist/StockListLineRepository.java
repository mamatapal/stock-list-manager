package app.stocklist.stocklist;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockListLineRepository extends JpaRepository<StockListLine, UUID> {
    List<StockListLine> findByBusinessIdAndStockListIdOrderBySortOrderAsc(UUID businessId, UUID stockListId);

    List<StockListLine> findByBusinessIdAndStockListIdInOrderBySortOrderAsc(UUID businessId,
                                                                            Collection<UUID> stockListIds);
}
