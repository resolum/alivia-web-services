package com.resolum.alivia.integration;

import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The whole application over a real PostgreSQL: the schema is built by Flyway alone (V1 to the last one) and Hibernate
 * only validates it, exactly as in production, and the requests go through the real server and the real security chain.
 * It is what H2 cannot show: migrations, locks, deadlocks, upserts, isolation levels and the wiring of every bean.
 *
 * <p>Skipped when Docker is not available. The classes that extend it share one context and one database, so each test
 * must create its own data with unique ids.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIf(value = "com.resolum.alivia.integration.PostgresContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no PostgreSQL to test against")
public abstract class PostgresIntegrationTest {

    @DynamicPropertySource
    static void realDatabase(DynamicPropertyRegistry registry) {
        var postgres = PostgresContainerSupport.container();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // The test profile builds the schema with Hibernate over H2; here the migrations build it and Hibernate checks it.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // The constraints must be right as the migrations leave them, not rewritten at startup.
        registry.add("alivia.persistence.sync-enum-checks", () -> "false");
    }

    @LocalServerPort
    protected int port;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private UserRepository users;
    @Autowired
    private TokenService tokens;
    @Value("${alivia.iam.super-admin.email}")
    private String superAdminEmail;

    /** Access token of the preloaded administrator. */
    protected String admin;

    private final HttpClient http = HttpClient.newBuilder().executor(Executors.newFixedThreadPool(64)).build();

    @BeforeEach
    void signInAsAdministrator() {
        var user = users.findByEmail(new EmailAddress(superAdminEmail));
        assertNotNull(user.orElse(null), "the preloaded administrator exists");
        admin = tokens.generateAccessToken(user.get());
        // The business that owns the inventory (the default business id of the test profile is 1).
        if (jdbc.queryForObject("select count(*) from businesses", Integer.class) == 0) {
            jdbc.update("insert into businesses(created_at, updated_at) values (now(), now())");
        }
    }

    protected record Response(int status, String body, String etag) {
    }

    protected HttpRequest.Builder request(String path, String token) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(60));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    protected Response call(String method, String path, String token, String body, String ifNoneMatch) throws Exception {
        var builder = request(path, token).method(method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (ifNoneMatch != null) {
            builder.header("If-None-Match", ifNoneMatch);
        }
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body(), response.headers().firstValue("ETag").orElse(null));
    }

    protected Response call(String method, String path, String token) throws Exception {
        return call(method, path, token, null, null);
    }

    protected java.util.concurrent.CompletableFuture<HttpResponse<String>> callAsync(String path, String token, String ifNoneMatch) {
        var builder = request(path, token).GET();
        if (ifNoneMatch != null) {
            builder.header("If-None-Match", ifNoneMatch);
        }
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected static long number(String json, String field) {
        var matcher = java.util.regex.Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("no \"" + field + "\" in " + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    protected static String text(String json, String field) {
        var matcher = java.util.regex.Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("no \"" + field + "\" in " + json);
        }
        return matcher.group(1);
    }
}
