package com.resolum.alivia.shared;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static verification of the hardened production profile.
 *
 * <p>A full startup test for {@code prod} needs a real PostgreSQL/MongoDB and the
 * Flyway baseline from AP-480, so this test validates the properties file directly.</p>
 */
class ProductionProfilePropertiesTest {

    private static final Properties PROD = load();

    private static final Map<String, String> REQUIRED = Map.ofEntries(
            Map.entry("server.port", "PORT"),
            Map.entry("spring.datasource.url", "DB_URL"),
            Map.entry("spring.datasource.username", "DB_USERNAME"),
            Map.entry("spring.datasource.password", "DB_PASSWORD"),
            Map.entry("spring.mongodb.uri", "MONGODB_URI"),
            Map.entry("spring.mongodb.database", "MONGODB_DATABASE"),
            Map.entry("alivia.iam.jwt.secret", "IAM_JWT_SECRET"),
            Map.entry("alivia.cors.allowed-origins", "CORS_ALLOWED_ORIGINS"),
            Map.entry("alivia.iam.email-verification.link-base-url", "IAM_EMAIL_VERIFICATION_LINK_BASE_URL"),
            Map.entry("stripe.secret-key", "STRIPE_SECRET_KEY"),
            Map.entry("stripe.publishable-key", "STRIPE_PUBLISHABLE_KEY"),
            Map.entry("stripe.webhook-secret", "STRIPE_WEBHOOK_SECRET"),
            Map.entry("cloudinary.cloud-name", "CLOUDINARY_CLOUD_NAME"),
            Map.entry("cloudinary.api-key", "CLOUDINARY_API_KEY"),
            Map.entry("cloudinary.api-secret", "CLOUDINARY_API_SECRET")
    );

    @Test
    void doesNotLogSqlStatements() {
        assertThat(PROD.getProperty("spring.jpa.show-sql")).isEqualTo("false");
        assertThat(PROD.getProperty("spring.jpa.properties.hibernate.format_sql")).isEqualTo("false");
        assertThat(PROD.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("INFO");
        assertThat(PROD.getProperty("logging.level.org.hibernate.orm.jdbc.bind")).isEqualTo("INFO");
    }

    @Test
    void disablesApiDocumentation() {
        assertThat(PROD.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(PROD.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    void requiresConnectionSecretsAndEndpointsWithoutDefaults() {
        REQUIRED.forEach((key, variable) ->
                assertThat(PROD.getProperty(key))
                        .as(key)
                        .isEqualTo("${" + variable + "}"));
    }

    @Test
    void validatesTheSchemaWithoutChangingIt() {
        // Flyway owns the schema (V1); Hibernate must never create or alter tables in prod.
        assertThat(PROD.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void keepsEmailFailClosedByDefault() {
        assertThat(PROD.getProperty("alivia.communications.email.provider"))
                .isEqualTo("${IAM_EMAIL_PROVIDER:unavailable}");
    }

    @Test
    void disablesDockerCompose() {
        assertThat(PROD.getProperty("spring.docker.compose.enabled")).isEqualTo("false");
    }

    private static Properties load() {
        var properties = new Properties();
        try (InputStream input = new ClassPathResource("application-prod.properties").getInputStream()) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("application-prod.properties is not readable", exception);
        }
        return properties;
    }
}
