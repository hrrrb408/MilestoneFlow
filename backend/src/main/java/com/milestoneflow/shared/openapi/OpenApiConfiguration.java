package com.milestoneflow.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) documentation configuration for MilestoneFlow.
 *
 * <p>Configures the API metadata, server URL, and cookie-based authentication
 * security scheme. Authentication uses HttpOnly cookies (MF_ACCESS, MF_REFRESH)
 * with CSRF protection via XSRF-TOKEN cookie — not JWT Bearer tokens.
 *
 * <h3>Authentication mechanism:</h3>
 * <ul>
 *   <li>{@code MF_ACCESS} — HttpOnly cookie carrying opaque access token</li>
 *   <li>{@code MF_REFRESH} — HttpOnly cookie carrying opaque refresh token</li>
 *   <li>{@code XSRF-TOKEN} — Cookie for CSRF protection (read by SPA, sent as header)</li>
 *   <li>{@code X-XSRF-TOKEN} — Request header for CSRF token validation</li>
 * </ul>
 *
 * <p>Swagger UI is available at {@code /swagger-ui.html} in local/test profiles only.
 * In production, Swagger UI is disabled via configuration.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI milestoneFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MilestoneFlow Backend API")
                        .version("v0.1")
                        .description("MilestoneFlow Pilot MVP v0.1 backend API. "
                                + "Authentication uses HttpOnly cookies (MF_ACCESS, MF_REFRESH) "
                                + "with CSRF protection. No JWT tokens are used."))
                .servers(List.of(new Server().url("/")))
                .addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
                .components(new Components()
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("MF_ACCESS")
                                .description("HttpOnly cookie containing the opaque access token. "
                                        + "Set by the server after successful login/refresh. "
                                        + "The MF_REFRESH cookie handles token rotation at /auth/refresh."))
                        .addSecuritySchemes("csrfToken", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("CSRF protection header. The XSRF-TOKEN cookie is set "
                                        + "by the server; the SPA must read it and send it as "
                                        + "X-XSRF-TOKEN header on state-changing requests.")));
    }
}
