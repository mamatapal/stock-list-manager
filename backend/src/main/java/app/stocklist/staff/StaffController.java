package app.stocklist.staff;

import app.stocklist.common.security.CurrentUser;
import app.stocklist.common.security.StepUpGuard;
import app.stocklist.common.util.ClientIp;
import app.stocklist.staff.StaffDtos.CreateStaffRequest;
import app.stocklist.staff.StaffDtos.CreateStaffResponse;
import app.stocklist.staff.StaffDtos.PermissionsResponse;
import app.stocklist.staff.StaffDtos.SessionInfo;
import app.stocklist.staff.StaffDtos.SetPermissionsRequest;
import app.stocklist.staff.StaffDtos.StaffResponse;
import app.stocklist.staff.StaffDtos.TemporaryPasswordResponse;
import app.stocklist.staff.StaffDtos.UpdateStaffRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff", description = "Needs STAFF_MANAGE. Changes also need X-Step-Up-Token")
@PreAuthorize("@perm.has('STAFF_MANAGE')")
public class StaffController {
    private final StaffService service;
    private final StepUpGuard stepUp;

    public StaffController(StaffService service, StepUpGuard stepUp) {
        this.service = service;
        this.stepUp = stepUp;
    }

    @GetMapping
    public List<StaffResponse> list() {
        return service.list(CurrentUser.get());
    }

    @GetMapping("/{id}")
    public StaffResponse get(@PathVariable UUID id) {
        return service.get(CurrentUser.get(), id);
    }

    @Operation(summary = "Create a staff account; returns a one-time temporary password")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "stepUp")})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateStaffResponse create(@Valid @RequestBody CreateStaffRequest body, HttpServletRequest req) {
        stepUp.require();
        return service.create(CurrentUser.get(), body, ClientIp.of(req));
    }

    @PutMapping("/{id}")
    public StaffResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateStaffRequest body,
                                HttpServletRequest req) {
        stepUp.require();
        return service.update(CurrentUser.get(), id, body, ClientIp.of(req));
    }

    @PostMapping("/{id}/activate")
    public StaffResponse activate(@PathVariable UUID id, HttpServletRequest req) {
        stepUp.require();
        return service.setActive(CurrentUser.get(), id, true, ClientIp.of(req));
    }

    @Operation(summary = "Deactivate: signs the staff member out everywhere immediately")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "stepUp")})
    @PostMapping("/{id}/deactivate")
    public StaffResponse deactivate(@PathVariable UUID id, HttpServletRequest req) {
        stepUp.require();
        return service.setActive(CurrentUser.get(), id, false, ClientIp.of(req));
    }

    @PostMapping("/{id}/reset-password")
    public TemporaryPasswordResponse resetPassword(@PathVariable UUID id, HttpServletRequest req) {
        stepUp.require();
        return service.resetPassword(CurrentUser.get(), id, ClientIp.of(req));
    }

    @GetMapping("/{id}/permissions")
    public PermissionsResponse permissions(@PathVariable UUID id) {
        return service.getPermissions(CurrentUser.get(), id);
    }

    @Operation(summary = "Replace the staff member's permissions (only ones you hold yourself)")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "stepUp")})
    @PutMapping("/{id}/permissions")
    public PermissionsResponse setPermissions(@PathVariable UUID id, @Valid @RequestBody SetPermissionsRequest body,
                                              HttpServletRequest req) {
        stepUp.require();
        return service.setPermissions(CurrentUser.get(), id, body.permissions(), ClientIp.of(req));
    }

    @GetMapping("/{id}/sessions")
    public List<SessionInfo> sessions(@PathVariable UUID id) {
        return service.sessions(CurrentUser.get(), id);
    }

    @DeleteMapping("/{id}/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSessions(@PathVariable UUID id, HttpServletRequest req) {
        stepUp.require();
        service.revokeSessions(CurrentUser.get(), id, ClientIp.of(req));
    }
}
