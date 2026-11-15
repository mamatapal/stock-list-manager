package app.stocklist.people;

import app.stocklist.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/people")
@Tag(name = "People", description = "Names of the owner and staff of your business, for 'created by' labels")
public class PeopleController {
    private final PeopleService service;

    public PeopleController(PeopleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Id, name, role and active flag of every user in your business")
    public List<PeopleService.PersonDto> list() {
        return service.list(CurrentUser.get());
    }
}
