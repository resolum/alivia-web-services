package com.resolum.alivia.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * One MongoDB for all the tests of a run that need the real database (Tracking: idempotent inserts, indexes, TTL and
 * query plans, which no double can show). It starts the first time a test asks for it and stops when the JVM ends.
 *
 * <p>It follows {@link PostgresContainerSupport}: without Docker those tests are skipped with a warning, and a build
 * that must not skip them sets {@value PostgresContainerSupport#REQUIRE_DOCKER} to {@code true} so a missing Docker
 * fails them instead. The image can be changed with {@value #IMAGE_PROPERTY} (or the environment variable
 * {@code ALIVIA_TESTS_MONGO_IMAGE}).</p>
 *
 * <p>The default image is the same major version as {@code compose.yaml}. Production runs the version of the Atlas
 * cluster, which is not recorded in this repository: keep the two aligned when it is confirmed.</p>
 */
public final class MongoContainerSupport {

    public static final String IMAGE_PROPERTY = "alivia.tests.mongo-image";
    static final String DEFAULT_IMAGE = "mongo:8.0";

    private static final Logger log = LoggerFactory.getLogger(MongoContainerSupport.class);
    private static MongoDBContainer container;

    private MongoContainerSupport() {
    }

    /** Used by {@code @EnabledIf}: whether the tests that need MongoDB run. */
    public static boolean dockerAvailable() {
        if (PostgresContainerSupport.dockerRequired()) {
            // Enabled whatever Docker says: a missing Docker fails them loudly when the container starts.
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
        log.warn("Docker is not available: the tests against a real MongoDB are SKIPPED. Set -D{}=true to fail instead.",
                PostgresContainerSupport.REQUIRE_DOCKER);
        return false;
    }

    static String image() {
        var value = System.getProperty(IMAGE_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv("ALIVIA_TESTS_MONGO_IMAGE");
        }
        return value == null || value.isBlank() ? DEFAULT_IMAGE : value.strip();
    }

    public static synchronized MongoDBContainer container() {
        if (container == null) {
            var created = new MongoDBContainer(image());
            created.start();
            Runtime.getRuntime().addShutdownHook(new Thread(created::stop));
            container = created;
        }
        return container;
    }

    /** The connection string of the shared MongoDB, for {@code spring.mongodb.uri}. */
    public static String connectionString() {
        return container().getConnectionString();
    }
}
