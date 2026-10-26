package com.resolum.alivia.shared;



import com.resolum.alivia.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigurationTest {

    private OpenAPI openApi;

    @BeforeEach
    void setUp() {
        var configuration = new OpenApiConfiguration();
        ReflectionTestUtils.setField(configuration, "applicationName", "Alivia Web Services");
        ReflectionTestUtils.setField(configuration, "applicationDescription", "API de Alivia");
        ReflectionTestUtils.setField(configuration, "applicationVersion", "1.0.0");

        openApi = configuration.VeyraPlatformOpenApi();
    }

    @Test
    void infoUsesConfiguredValues() {
        var info = openApi.getInfo();

        assertEquals("Alivia Web Services", info.getTitle());
        assertEquals("API de Alivia", info.getDescription());
        assertEquals("1.0.0", info.getVersion());
    }

    @Test
    void licenseIsApache() {
        var license = openApi.getInfo().getLicense();

        assertEquals("Apache 2.0", license.getName());
        assertTrue(license.getUrl().endsWith("springdoc.org"));
    }

    @Test
    void externalDocsPointToProjectWiki() {
        var docs = openApi.getExternalDocs();

        assertEquals("Veyra Platform Wiki Documentation", docs.getDescription());
        assertEquals("https://github.com/NovaPeru-Tech/NovaPeru-Tech-Project-Report", docs.getUrl());
    }

    @Test
    void bearerSecuritySchemeIsRegistered() {
        var scheme = openApi.getComponents().getSecuritySchemes().get("bearerAuth");

        assertNotNull(scheme);
        assertEquals("bearerAuth", scheme.getName());
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
    }

    @Test
    void bearerSecurityRequirementIsApplied() {
        var requirements = openApi.getSecurity();

        assertEquals(1, requirements.size());
        assertTrue(requirements.getFirst().containsKey("bearerAuth"));
    }
}