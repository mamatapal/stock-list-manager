package app.stocklist.platformadmin;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/** /admin opens the Supplier Administration page (static files in resources/static/admin). */
@Controller
@Hidden
public class AdminPageController {
    @GetMapping({"/admin", "/admin/"})
    public RedirectView page() {
        return new RedirectView("/admin/index.html");
    }
}
