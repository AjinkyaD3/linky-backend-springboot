package com.ajinkya.linky.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Interactive API docs at /swagger-ui.html. Disabled unless API_DOCS_ENABLED=true (on in the local profile). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI linkyOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Linky API").version("1.0")
                        .description("URL shortener: links, one-time and password-protected links, QR codes, analytics."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
