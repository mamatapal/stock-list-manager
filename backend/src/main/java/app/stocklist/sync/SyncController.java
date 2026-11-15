package app.stocklist.sync;

import app.stocklist.common.security.CurrentUser;
import app.stocklist.sync.SyncDtos.PullResponse;
import app.stocklist.sync.SyncDtos.PushRequest;
import app.stocklist.sync.SyncDtos.PushResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
@Tag(name = "Sync", description = "Offline-first synchronisation for the Android app")
public class SyncController {
    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    @PostMapping("/push")
    @Operation(summary = "Apply up to 50 queued changes in order. Each gets APPLIED, CONFLICT or REJECTED; "
            + "retrying an opId returns its stored result")
    public PushResponse push(@Valid @RequestBody PushRequest body) {
        return new PushResponse(service.push(CurrentUser.get(), body.ops()));
    }

    @GetMapping("/pull")
    @Operation(summary = "Records changed after `since` that the caller may see, deleted ones included")
    public PullResponse pull(@RequestParam(defaultValue = "0") long since,
                             @RequestParam(defaultValue = "500") int limit) {
        return service.pull(CurrentUser.get(), since, limit);
    }
}
