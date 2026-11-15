package app.stocklist.item;

import app.stocklist.common.security.CurrentUser;
import app.stocklist.item.ItemDtos.ItemDto;
import app.stocklist.item.ItemDtos.ItemInput;
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
@RequestMapping("/api/v1/items")
@Tag(name = "Items", description = "ITEM_VIEW / ADD / EDIT / DELETE plus price permissions. Updates need If-Match")
public class ItemController {
    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search items by name or category; optional exact category filter")
    public List<ItemDto> search(@RequestParam(required = false) String q,
                                @RequestParam(required = false) String category,
                                @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.search(CurrentUser.get(), q, category, includeInactive);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return service.categories(CurrentUser.get());
    }

    @GetMapping("/{id}")
    public ItemDto get(@PathVariable UUID id) {
        return service.get(CurrentUser.get(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDto create(@RequestParam(required = false) UUID id, @Valid @RequestBody ItemInput body) {
        return service.create(CurrentUser.get(), id == null ? UUID.randomUUID() : id, body);
    }

    @PutMapping("/{id}")
    public ItemDto update(@PathVariable UUID id, @RequestHeader("If-Match") long version,
                          @Valid @RequestBody ItemInput body) {
        return service.update(CurrentUser.get(), id, version, body);
    }

    @DeleteMapping("/{id}")
    public ItemDto delete(@PathVariable UUID id, @RequestHeader("If-Match") long version) {
        return service.delete(CurrentUser.get(), id, version);
    }
}
