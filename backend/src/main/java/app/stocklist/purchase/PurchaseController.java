package app.stocklist.purchase;

import app.stocklist.common.security.CurrentUser;
import app.stocklist.purchase.PurchaseDtos.CancelInput;
import app.stocklist.purchase.PurchaseDtos.DeliveryInput;
import app.stocklist.purchase.PurchaseDtos.PurchaseDto;
import app.stocklist.purchase.PurchaseDtos.PurchaseInput;
import app.stocklist.purchase.PurchaseDtos.RevisionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchases")
@Tag(name = "Purchases and receiving", description = "PURCHASE_RECORD / EDIT / DELETE; price fields need "
        + "PURCHASE_PRICE_ENTER / SELLING_PRICE_ENTER; profit fields are returned only with EXPECTED_PROFIT_VIEW")
public class PurchaseController {
    private final PurchaseService service;
    private final HistoryService history;

    public PurchaseController(PurchaseService service, HistoryService history) {
        this.service = service;
        this.history = history;
    }

    @GetMapping("/by-supplier/{supplierId}")
    @Operation(summary = "Supplier-wise history: purchases (drafts excluded), count, total spent, first and last date")
    public HistoryService.SupplierHistory bySupplier(@PathVariable UUID supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return history.supplier(CurrentUser.get(), supplierId, from, to);
    }

    @GetMapping("/by-item/{itemId}")
    @Operation(summary = "Item-wise history: every billed line of the item with its historical quantity and prices")
    public HistoryService.ItemHistory byItem(@PathVariable UUID itemId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return history.item(CurrentUser.get(), itemId, from, to);
    }

    @GetMapping
    @Operation(summary = "Search by invoice, supplier, item or notes; filter by supplier, status and purchase date")
    public List<PurchaseDto> search(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) UUID supplierId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.search(CurrentUser.get(), q, supplierId, status, from, to);
    }

    @GetMapping("/{id}")
    public PurchaseDto get(@PathVariable UUID id) {
        return service.get(CurrentUser.get(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a draft or a completed purchase; optionally record the goods that came with the bill")
    public PurchaseDto create(@RequestParam(required = false) UUID id, @Valid @RequestBody PurchaseInput body) {
        return service.create(CurrentUser.get(), id == null ? UUID.randomUUID() : id, body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit (a completed purchase needs PURCHASE_EDIT and keeps the previous state as a revision)")
    public PurchaseDto update(@PathVariable UUID id, @RequestHeader("If-Match") long version,
                              @Valid @RequestBody PurchaseInput body) {
        return service.update(CurrentUser.get(), id, version, body);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft. Completed purchases cannot be deleted: use cancel")
    public PurchaseDto deleteDraft(@PathVariable UUID id, @RequestHeader("If-Match") long version) {
        return service.deleteOrCancel(CurrentUser.get(), id, version, null);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a completed purchase with a reason (kept in history, excluded from totals)")
    public PurchaseDto cancel(@PathVariable UUID id, @RequestHeader("If-Match") long version,
                              @Valid @RequestBody CancelInput body) {
        return service.deleteOrCancel(CurrentUser.get(), id, version, body.reason());
    }

    @PostMapping("/{id}/deliveries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record goods received (or a correction). Sending the same delivery id again records it once")
    public PurchaseDto receive(@PathVariable UUID id, @Valid @RequestBody DeliveryInput body) {
        return service.addDelivery(CurrentUser.get(), id, body);
    }

    @GetMapping("/{id}/revisions")
    @Operation(summary = "Earlier versions of a completed purchase, oldest first")
    public List<RevisionDto> revisions(@PathVariable UUID id) {
        return service.revisions(CurrentUser.get(), id);
    }
}
