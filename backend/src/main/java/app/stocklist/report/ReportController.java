package app.stocklist.report;

import app.stocklist.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Completed purchases only. PURCHASE_HISTORY_VIEW; profit figures need "
        + "EXPECTED_PROFIT_VIEW; the expected-profit report needs PROFIT_REPORT_VIEW too. Expected profit is an "
        + "estimate from selling prices, never realised profit")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/purchases")
    @Operation(summary = "Purchases in a period (optionally one supplier or one item), with totals")
    public ReportService.PurchaseReport purchases(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                                  @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                                  @RequestParam(required = false) UUID supplierId,
                                                  @RequestParam(required = false) UUID itemId) {
        return service.purchases(CurrentUser.get(), from, to, supplierId, itemId);
    }

    @GetMapping("/supplier-totals")
    @Operation(summary = "Total bought from each supplier in a period")
    public ReportService.SupplierTotals supplierTotals(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                                       @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return service.supplierTotals(CurrentUser.get(), from, to);
    }

    @GetMapping("/item-prices")
    @Operation(summary = "Purchase prices per item and unit in a period: lowest, highest, average, latest")
    public ReportService.ItemPrices itemPrices(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                               @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) UUID supplierId) {
        return service.itemPrices(CurrentUser.get(), from, to, supplierId);
    }

    @GetMapping("/expected-profit")
    @Operation(summary = "Expected (not realised) profit by supplier and item")
    public ReportService.ExpectedProfitReport expectedProfit(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                                             @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return service.expectedProfit(CurrentUser.get(), from, to);
    }
}
