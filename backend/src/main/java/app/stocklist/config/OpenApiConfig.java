package app.stocklist.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description served at /v3/api-docs and Swagger UI at /swagger-ui.html.
 * Both are off unless SWAGGER_ENABLED=true (on for local development, off in production).
 */
@Configuration
public class OpenApiConfig {
    public static final String BEARER = "bearerAuth";
    public static final String STEP_UP = "stepUp";

    @Bean
    public OpenAPI stockListOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stock List Manager API")
                        .version("v1")
                        .description("""
                                Authentication, staff and business APIs (Phase 1).

                                **Signing in:** call `POST /api/v1/auth/login` (or `register-business`), then click
                                *Authorize* and paste the `accessToken`. Access tokens last 15 minutes; use
                                `POST /api/v1/auth/refresh` with the `refreshToken` to get a new pair (the old refresh
                                token stops working; presenting it again ends the whole session).

                                **Sensitive actions** (staff, permissions, business settings) also need a step-up token:
                                call `POST /api/v1/auth/reauth` with your password and send the returned `stepUpToken`
                                in the `X-Step-Up-Token` header. It lasts 5 minutes.

                                **Errors** are `application/problem+json` with a stable `code`, e.g. `INVALID_CREDENTIALS`,
                                `PERMISSION_DENIED`, `STEP_UP_REQUIRED`, `CANNOT_GRANT`, `USERNAME_TAKEN`.
                                Records belonging to another business always answer 404."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Access token from login, register-business or refresh"))
                        .addSecuritySchemes(STEP_UP, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Step-Up-Token")
                                .description("Step-up token from POST /api/v1/auth/reauth (5 minutes)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
