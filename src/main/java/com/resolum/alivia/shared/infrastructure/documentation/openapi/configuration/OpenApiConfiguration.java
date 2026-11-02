package com.resolum.alivia.shared.infrastructure.documentation.openapi.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
        @Value("${spring.application.name:Alivia Web Services}")
        String applicationName;

        @Value("${documentation.application.description:Alivia Web Services API Documentation}")
        String applicationDescription;

        @Value("${documentation.application.version:1.0.0}")
        String applicationVersion;

        @Bean
        public OpenAPI VeyraPlatformOpenApi() {
                var openApi = new OpenAPI();

                // General configuration
                openApi
                                .info(new Info()
                                                .title(this.applicationName)
                                                .description(this.applicationDescription)
                                                .version(this.applicationVersion)
                                                .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                                .externalDocs(new ExternalDocumentation()
                                                .description("Veyra Platform Wiki Documentation")
                                                .url("https://github.com/NovaPeru-Tech/NovaPeru-Tech-Project-Report"));
                // Return the OpenAPI configuration instance
                final String securitySchemeName = "bearerAuth";

                openApi.addSecurityItem(new SecurityRequirement()
                                .addList(securitySchemeName))
                                .components(new Components()
                                                .addSecuritySchemes(securitySchemeName,
                                                                new SecurityScheme()
                                                                                .name(securitySchemeName)
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")));
                return openApi;
        }
}