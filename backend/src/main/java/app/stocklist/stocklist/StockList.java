package app.stocklist.stocklist;

import app.stocklist.common.sync.SyncEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stock_list")
public class StockList extends SyncEntity {
    public static final String DRAFT = "DRAFT";
    public static final String SAVED = "SAVED";

    private String title;
    private LocalDate listDate;
    private UUID supplierId;
    private String notes;
    private String status;
    /** PENDING until goods are received against it (Phase 4 updates this). */
    private String receivingStatus;
    private BigDecimal totalAmount;

    protected StockList() {}

    public StockList(UUID id, UUID businessId, UUID userId, Instant now, long seq) {
        initNew(id, businessId, userId, now, seq);
        this.receivingStatus = "PENDING";
    }

    public void apply(String title, LocalDate listDate, UUID supplierId, String notes, String status,
                      BigDecimal totalAmount) {
        this.title = title;
        this.listDate = listDate;
        this.supplierId = supplierId;
        this.notes = notes;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public String getTitle() { return title; }
    public LocalDate getListDate() { return listDate; }
    public UUID getSupplierId() { return supplierId; }
    public String getNotes() { return notes; }
    public String getStatus() { return status; }
    public String getReceivingStatus() { return receivingStatus; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
