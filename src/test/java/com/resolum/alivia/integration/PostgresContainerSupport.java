package com.resolum.alivia.integration;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL for all the tests of a run that need the real database. It starts the first time a test asks for it and
 * stops when the JVM ends. Without Docker those tests are skipped, so the suite still passes on a machine that has none.
 */
public final class PostgresContainerSupport {

    private static final String IMAGE = "postgres:17";
    private static PostgreSQLContainer container;

    private PostgresContainerSupport() {
    }

    /** Used by {@code @EnabledIf}: whether a Docker daemon answers. */
    public static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable unavailable) {
            return false;
        }
    }

    public static synchronized PostgreSQLContainer container() {
        if (container == null) {
            var created = new PostgreSQLContainer(IMAGE);
            created.start();
            Runtime.getRuntime().addShutdownHook(new Thread(created::stop));
            container = created;
        }
        return container;
    }
}
