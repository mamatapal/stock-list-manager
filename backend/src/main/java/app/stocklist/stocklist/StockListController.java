package app.stocklist.stocklist;

import app.stocklist.common.security.CurrentUser;
import app.stocklist.stocklist.StockListDtos.StockListDto;
import app.stocklist.stocklist.StockListDtos.StockListInput;
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
@RequestMapping("/api/v1/stock-lists")
@Tag(name = "Out-of-stock lists", description = "LIST_CREATE / EDIT / DELETE. Lines are saved with their list")
public class StockListController {
    private final StockListService service;

    public StockListController(StockListService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search by title, notes or item name; filter by supplier, status and date range (yyyy-MM-dd)")
    public List<StockListDto> search(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) UUID supplierId,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.search(CurrentUser.get(), q, supplierId, status, from, to);
    }

    @GetMapping("/{id}/receiving")
    @Operation(summary = "The list with requested, received and remaining quantity per line (same as GET /{id})")
    public StockListDto receiving(@PathVariable UUID id) {
        return service.get(CurrentUser.get(), id);
    }

    @GetMapping("/{id}")
    public StockListDto get(@PathVariable UUID id) {
        return service.get(CurrentUser.get(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a list with its lines. Totals are calculated by the server")
    public StockListDto create(@RequestParam(required = false) UUID id, @Valid @RequestBody StockListInput body) {
        return service.create(CurrentUser.get(), id == null ? UUID.randomUUID() : id, body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace the list and its lines (lines not sent are removed)")
    public StockListDto update(@PathVariable UUID id, @RequestHeader("If-Match") long version,
                               @Valid @RequestBody StockListInput body) {
        return service.update(CurrentUser.get(), id, version, body);
    }

    @DeleteMapping("/{id}")
    public StockListDto delete(@PathVariable UUID id, @RequestHeader("If-Match") long version) {
        return service.delete(CurrentUser.get(), id, version);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Copy a list into a new draft dated today")
    public StockListDto duplicate(@PathVariable UUID id, @RequestParam(required = false) UUID newId) {
        return service.duplicate(CurrentUser.get(), id, newId == null ? UUID.randomUUID() : newId);
    }
}
