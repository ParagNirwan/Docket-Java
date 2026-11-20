package com.docket.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Describes the public API (springdoc.paths-to-match=/api/public/**). Served at /v3/api-docs.
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI docketPublicApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Docket Public API")
                        .version("v1")
                        .description("Programmatic access for API keys and service accounts. "
                                + "Each credential belongs to one organization and is limited by its scopes "
                                + "(documents:read, documents:write). See docs/developer-platform.md."))
                .components(new Components()
                        .addSecuritySchemes("apiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("An API key created under Settings → Developers"))
                        .addSecuritySchemes("serviceAccount", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("A service-account token")))
                .addSecurityItem(new SecurityRequirement().addList("apiKey"))
                .addSecurityItem(new SecurityRequirement().addList("serviceAccount"));
    }
}
