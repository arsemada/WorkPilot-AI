package com.workpilot.ai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI workPilotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WorkPilot AI API")
                        .version("0.1.0")
                        .description("Accounts, organizations, memberships, cookie sessions, and organization tasks."))
                .components(new Components()
                        .addSecuritySchemes("sessionCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("WORKPILOT_SESSION")
                                .description("HttpOnly session cookie issued by login."))
                        .addSecuritySchemes("csrfHeader", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("CSRF token from GET /api/auth/csrf. Send it on POST, PATCH, and DELETE.")))
                .addSecurityItem(new SecurityRequirement().addList("sessionCookie").addList("csrfHeader"));
    }
}
