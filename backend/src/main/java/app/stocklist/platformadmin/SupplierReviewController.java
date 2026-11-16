package app.stocklist.platformadmin;

import app.stocklist.common.util.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Supplier review actions of the Supplier Administration portal. Decisions need If-Match: <supplier version>. */
@RestController
@RequestMapping("/api/v1/admin/suppliers")
@Tag(name = "Supplier Administration")
public class SupplierReviewController {
    private final SupplierReviewService reviews;

    public SupplierReviewController(SupplierReviewService reviews) {
        this.reviews = reviews;
    }

    @Operation(summary = "Suppliers, optionally only one status (PENDING_APPROVAL, APPROVED, REJECTED, SUSPENDED, "
            + "PENDING_EMAIL_VERIFICATION). Without a filter, those waiting for approval come first")
    @GetMapping
    public List<AdminDtos.SupplierSummary> list(@RequestParam(required = false) String status) {
        AdminPrincipal.current();
        return reviews.list(status);
    }

    @Operation(summary = "Full details, users, possible duplicates and history of one supplier")
    @GetMapping("/{id}")
    public AdminDtos.SupplierDetail get(@PathVariable UUID id) {
        AdminPrincipal.current();
        return reviews.detail(id);
    }

    @Operation(summary = "Approve a supplier that is waiting for approval")
    @PostMapping("/{id}/approve")
    public AdminDtos.SupplierDetail approve(@PathVariable UUID id, @RequestHeader("If-Match") long version,
                                            HttpServletRequest req) {
        return reviews.approve(AdminPrincipal.current(), id, version, ClientIp.of(req));
    }

    @Operation(summary = "Reject a supplier that is waiting for approval (reason required; the supplier can correct "
            + "the details and send them again)")
    @PostMapping("/{id}/reject")
    public AdminDtos.SupplierDetail reject(@PathVariable UUID id, @RequestHeader("If-Match") long version,
                                           @RequestBody AdminDtos.ReasonRequest body, HttpServletRequest req) {
        return reviews.reject(AdminPrincipal.current(), id, version, body.reason(), ClientIp.of(req));
    }

    @Operation(summary = "Suspend an approved supplier (reason required)")
    @PostMapping("/{id}/suspend")
    public AdminDtos.SupplierDetail suspend(@PathVariable UUID id, @RequestHeader("If-Match") long version,
                                            @RequestBody AdminDtos.ReasonRequest body, HttpServletRequest req) {
        return reviews.suspend(AdminPrincipal.current(), id, version, body.reason(), ClientIp.of(req));
    }

    @Operation(summary = "Reinstate a suspended supplier")
    @PostMapping("/{id}/reinstate")
    public AdminDtos.SupplierDetail reinstate(@PathVariable UUID id, @RequestHeader("If-Match") long version,
                                              HttpServletRequest req) {
        return reviews.reinstate(AdminPrincipal.current(), id, version, ClientIp.of(req));
    }
}
