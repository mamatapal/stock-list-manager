package app.stocklist.stocklist;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockListRepository extends JpaRepository<StockList, UUID> {

    Optional<StockList> findByBusinessIdAndId(UUID businessId, UUID id);

    @Query("select s from StockList s where s.businessId = :businessId and s.deletedAt is null "
            + "and (:supplierId is null or s.supplierId = :supplierId) "
            + "and (:status is null or s.status = :status) "
            + "and (:fromDate is null or s.listDate >= :fromDate) and (:toDate is null or s.listDate <= :toDate) "
            + "and (:q is null or lower(s.title) like :q or lower(s.notes) like :q or exists ("
            + "  select 1 from StockListLine l where l.stockListId = s.id and lower(l.itemName) like :q)) "
            + "order by s.listDate desc, s.updatedAt desc")
    List<StockList> search(UUID businessId, String q, UUID supplierId, String status, LocalDate fromDate,
                           LocalDate toDate);

    /**
     * Receiving progress is derived from deliveries: it changes the change number (so phones fetch it) but not the
     * version, so recording goods never makes someone's list edit look like a conflict.
     */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StockList s set s.receivingStatus = :status, s.changeSeq = :seq "
            + "where s.businessId = :businessId and s.id = :id")
    int setReceiving(UUID businessId, UUID id, String status, long seq);

    List<StockList> findByBusinessIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(UUID businessId, long since,
                                                                              Pageable page);
}
