package app.stocklist.purchase;

import app.stocklist.common.sync.SyncEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A supplier bill. Lines, deliveries and revisions hang off it. One version covers the header and lines. */
@Entity
@Table(name = "purchase")
public class Purchase extends SyncEntity {
    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String PARTIAL = "PARTIAL";
    public static final String RECEIVED = "RECEIVED";
    public static final String CANCELLED = "CANCELLED";

    private UUID supplierId;
    private String invoiceNo;
    private LocalDate purchaseDate;
    private UUID stockListId;
    private String notes;
    private String status;
    private LocalDate receivedDate;
    private boolean overReceived;
    private int itemCount;
    private int revisionNo;
    private BigDecimal totalCost;
    private BigDecimal totalExpectedSales;
    private BigDecimal totalExpectedProfit;
    private BigDecimal pricedCost;
    private Instant completedAt;
    private UUID completedBy;
    private Instant cancelledAt;
    private String cancelReason;

    protected Purchase() {}

    public Purchase(UUID id, UUID businessId, UUID userId, Instant now, long seq) {
        initNew(id, businessId, userId, now, seq);
        this.status = DRAFT;
    }

    public void apply(UUID supplierId, String invoiceNo, LocalDate purchaseDate, UUID stockListId, String notes,
                      int itemCount, PurchaseCalculator.Totals totals) {
        this.supplierId = supplierId;
        this.invoiceNo = invoiceNo;
        this.purchaseDate = purchaseDate;
        this.stockListId = stockListId;
        this.notes = notes;
        this.itemCount = itemCount;
        this.totalCost = totals.cost();
        this.totalExpectedSales = totals.sales();
        this.totalExpectedProfit = totals.profit();
        this.pricedCost = totals.pricedCost();
    }

    void complete(UUID userId, Instant now) {
        this.status = PENDING;
        this.completedAt = now;
        this.completedBy = userId;
    }

    void cancel(String reason, Instant now) {
        this.status = CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = now;
    }

    void receiving(String status, LocalDate receivedDate, boolean overReceived) {
        this.status = status;
        this.receivedDate = receivedDate;
        this.overReceived = overReceived;
    }

    void nextRevision() { this.revisionNo++; }

    public boolean isDraft() { return DRAFT.equals(status); }
    public boolean isCancelled() { return CANCELLED.equals(status); }
    public boolean isCompleted() { return !isDraft() && !isCancelled(); }

    public UUID getSupplierId() { return supplierId; }
    public String getInvoiceNo() { return invoiceNo; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public UUID getStockListId() { return stockListId; }
    public String getNotes() { return notes; }
    public String getStatus() { return status; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public boolean isOverReceived() { return overReceived; }
    public int getItemCount() { return itemCount; }
    public int getRevisionNo() { return revisionNo; }
    public BigDecimal getTotalCost() { return totalCost; }
    public BigDecimal getTotalExpectedSales() { return totalExpectedSales; }
    public BigDecimal getTotalExpectedProfit() { return totalExpectedProfit; }
    public BigDecimal getPricedCost() { return pricedCost; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getCompletedBy() { return completedBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
}
