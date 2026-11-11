package com.resolum.alivia.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL for all the tests of a run that need the real database. It starts the first time a test asks for it and
 * stops when the JVM ends.
 *
 * <p>Without Docker those tests are skipped, so the suite still passes on a machine that has none, and the skip is
 * logged as a warning. A build that must not skip them (the CI) sets {@value #REQUIRE_DOCKER} to {@code true}: then a
 * missing Docker fails the tests instead of hiding them.</p>
 *
 * <p>The image is the major version that production and {@code compose.yaml} run, and can be changed with
 * {@value #IMAGE_PROPERTY} (or the environment variable {@code ALIVIA_TESTS_POSTGRES_IMAGE}).</p>
 */
public final class PostgresContainerSupport {

    /** System property (or {@code ALIVIA_TESTS_REQUIRE_DOCKER}): fail instead of skipping when Docker is missing. */
    public static final String REQUIRE_DOCKER = "alivia.tests.require-docker";
    public static final String IMAGE_PROPERTY = "alivia.tests.postgres-image";
    static final String DEFAULT_IMAGE = "postgres:18";

    private static final Logger log = LoggerFactory.getLogger(PostgresContainerSupport.class);
    private static PostgreSQLContainer container;

    private PostgresContainerSupport() {
    }

    /** Used by {@code @EnabledIf}: whether the tests that need PostgreSQL run. */
    public static boolean dockerAvailable() {
        if (dockerRequired()) {
            // The tests are enabled whatever Docker says: a missing Docker fails them loudly when the container starts.
            return true;
        }
        boolean present;
        try {
            present = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable unavailable) {
            present = false;
        }
        return run(present, false);
    }

    /**
     * Whether the tests run: when Docker is required they always do (and fail if it is missing); otherwise only when it
     * is present, and a skip is announced in the log so that it does not go unnoticed.
     */
    static boolean run(boolean dockerPresent, boolean dockerRequired) {
        if (dockerRequired || dockerPresent) {
            return true;
        }
        log.warn("Docker is not available: the tests against a real PostgreSQL are SKIPPED. Set -D{}=true to fail instead.",
                REQUIRE_DOCKER);
        return false;
    }

    static boolean dockerRequired() {
        return Boolean.parseBoolean(setting(REQUIRE_DOCKER, "ALIVIA_TESTS_REQUIRE_DOCKER", "false"));
    }

    static String image() {
        return setting(IMAGE_PROPERTY, "ALIVIA_TESTS_POSTGRES_IMAGE", DEFAULT_IMAGE);
    }

    private static String setting(String property, String environmentVariable, String fallback) {
        var value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    public static synchronized PostgreSQLContainer container() {
        if (container == null) {
            var created = new PostgreSQLContainer(image());
            created.start();
            Runtime.getRuntime().addShutdownHook(new Thread(created::stop));
            container = created;
        }
        return container;
    }
}
