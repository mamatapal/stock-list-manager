package app.stocklist.purchase;

import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/batches")
@Tag(name = "Batches and expiry", description = "EXPIRY_VIEW. Each batch links to the purchase it came from")
public class BatchController {
    private final BatchService service;

    public BatchController(BatchService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Batch tracking. expiry = EXPIRED, WITHIN_7, WITHIN_30, WITHIN_90 or ALL gives an expiry "
            + "list (only stock still there, cancelled purchases left out), soonest first")
    public List<BatchService.BatchDto> search(
            @RequestParam(required = false) String expiry, @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID itemId, @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedTo,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        ExpiryCalculator.Filter filter = null;
        if (expiry != null && !expiry.isBlank()) {
            try {
                filter = ExpiryCalculator.Filter.valueOf(expiry.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("INVALID_FILTER", "expiry must be EXPIRED, WITHIN_7, WITHIN_30, WITHIN_90 or ALL");
            }
        }
        return service.search(CurrentUser.get(), filter, q, itemId, supplierId, receivedFrom, receivedTo,
                includeCancelled, today);
    }

    @GetMapping("/alert")
    @Operation(summary = "How many batches are expired, and due within 7 and 30 days (for the dashboard)")
    public BatchService.ExpiryAlert alert() {
        return service.alert(CurrentUser.get());
    }

    @GetMapping("/{id}")
    public BatchService.BatchDto get(@PathVariable UUID id) {
        return service.get(CurrentUser.get(), id);
    }
}
