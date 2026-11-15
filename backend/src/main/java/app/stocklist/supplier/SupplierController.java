package app.stocklist.supplier;

import app.stocklist.common.security.CurrentUser;
import app.stocklist.supplier.SupplierDtos.SupplierDto;
import app.stocklist.supplier.SupplierDtos.SupplierInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/suppliers")
@Tag(name = "Suppliers", description = "SUPPLIER_VIEW / ADD / EDIT / DELETE. Updates and deletes need If-Match: <version>")
public class SupplierController {
    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search suppliers by name, contact, phone or city")
    public List<SupplierDto> search(@RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.search(CurrentUser.get(), q, includeInactive);
    }

    @GetMapping("/{id}")
    public SupplierDto get(@PathVariable UUID id) {
        return service.get(CurrentUser.get(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a supplier. Optional ?id= lets the app choose the id (used for offline records)")
    public SupplierDto create(@RequestParam(required = false) UUID id, @Valid @RequestBody SupplierInput body) {
        return service.create(CurrentUser.get(), id == null ? UUID.randomUUID() : id, body);
    }

    @PutMapping("/{id}")
    public SupplierDto update(@PathVariable UUID id, @RequestHeader("If-Match") long version,
                              @Valid @RequestBody SupplierInput body) {
        return service.update(CurrentUser.get(), id, version, body);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete: hidden everywhere, kept for history")
    public SupplierDto delete(@PathVariable UUID id, @RequestHeader("If-Match") long version) {
        return service.delete(CurrentUser.get(), id, version);
    }
}
