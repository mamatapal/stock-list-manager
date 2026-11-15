package app.stocklist.business;

import app.stocklist.business.BusinessDtos.BusinessResponse;
import app.stocklist.business.BusinessDtos.UpdateBusinessRequest;
import app.stocklist.common.security.CurrentUser;
import app.stocklist.common.security.StepUpGuard;
import app.stocklist.common.util.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business")
@Tag(name = "Business profile")
public class BusinessController {
    private final BusinessService service;
    private final StepUpGuard stepUp;

    public BusinessController(BusinessService service, StepUpGuard stepUp) {
        this.service = service;
        this.stepUp = stepUp;
    }

    /** Any signed-in user: the details are printed on PDFs. */
    @GetMapping
    public BusinessResponse get() {
        return service.get(CurrentUser.get());
    }

    @Operation(summary = "Update the business profile (SETTINGS_MANAGE + step-up)")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "stepUp")})
    @PutMapping
    @PreAuthorize("@perm.has('SETTINGS_MANAGE')")
    public BusinessResponse update(@Valid @RequestBody UpdateBusinessRequest body, HttpServletRequest req) {
        stepUp.require();
        return service.update(CurrentUser.get(), body, ClientIp.of(req));
    }
}
