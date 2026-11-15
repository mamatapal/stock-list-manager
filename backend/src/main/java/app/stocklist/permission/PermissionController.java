package app.stocklist.permission;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permissions")
public class PermissionController {

    public record PermissionInfo(String code, boolean staffDefault) {}

    /** The catalogue, for the permission editor screen. */
    @GetMapping
    public List<PermissionInfo> catalogue() {
        return Arrays.stream(Permission.values()).map(p -> new PermissionInfo(p.name(), p.staffDefault())).toList();
    }
}
