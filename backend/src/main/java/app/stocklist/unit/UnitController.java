package app.stocklist.unit;

import app.stocklist.common.security.CurrentUser;
import app.stocklist.unit.UnitDtos.UnitDto;
import app.stocklist.unit.UnitDtos.UnitInput;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/units")
@Tag(name = "Units", description = "14 built-in units plus the business's custom units")
public class UnitController {
    private final UnitService service;

    public UnitController(UnitService service) {
        this.service = service;
    }

    @GetMapping
    public List<UnitDto> list() {
        return service.list(CurrentUser.get());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnitDto create(@RequestParam(required = false) UUID id, @Valid @RequestBody UnitInput body) {
        return service.create(CurrentUser.get(), id == null ? UUID.randomUUID() : id, body);
    }

    @DeleteMapping("/{id}")
    public UnitDto delete(@PathVariable UUID id, @RequestHeader("If-Match") long version) {
        return service.delete(CurrentUser.get(), id, version);
    }
}
