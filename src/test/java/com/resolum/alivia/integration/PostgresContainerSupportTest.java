package com.resolum.alivia.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the tests against a real PostgreSQL decide to run, and which PostgreSQL they use. No Docker needed. */
class PostgresContainerSupportTest {

    @AfterEach
    void clearSettings() {
        System.clearProperty(PostgresContainerSupport.REQUIRE_DOCKER);
        System.clearProperty(PostgresContainerSupport.IMAGE_PROPERTY);
    }

    @Test
    void theTestsRunWhenDockerIsPresentAndAreSkippedWhenItIsNot() {
        assertTrue(PostgresContainerSupport.run(true, false));
        assertFalse(PostgresContainerSupport.run(false, false));
    }

    @Test
    void whenDockerIsRequiredTheTestsAlwaysRunSoAMissingDockerFailsThemInsteadOfHidingThem() {
        assertTrue(PostgresContainerSupport.run(false, true));
        assertTrue(PostgresContainerSupport.run(true, true));
    }

    @Test
    void requiringDockerIsASystemPropertyAndOffByDefault() {
        assertFalse(PostgresContainerSupport.dockerRequired());
        System.setProperty(PostgresContainerSupport.REQUIRE_DOCKER, "true");
        assertTrue(PostgresContainerSupport.dockerRequired());
        System.setProperty(PostgresContainerSupport.REQUIRE_DOCKER, "false");
        assertFalse(PostgresContainerSupport.dockerRequired());
        System.setProperty(PostgresContainerSupport.REQUIRE_DOCKER, "garbage");
        assertFalse(PostgresContainerSupport.dockerRequired());
    }

    @Test
    void whenRequiredTheTestsAreEnabledWithoutAskingDocker() {
        System.setProperty(PostgresContainerSupport.REQUIRE_DOCKER, "true");

        assertTrue(PostgresContainerSupport.dockerAvailable());
    }

    @Test
    void theImageIsTheMajorVersionThatProductionRunsAndCanBeChanged() {
        assertEquals("postgres:18", PostgresContainerSupport.image());
        System.setProperty(PostgresContainerSupport.IMAGE_PROPERTY, " postgres:17 ");
        assertEquals("postgres:17", PostgresContainerSupport.image());
        System.setProperty(PostgresContainerSupport.IMAGE_PROPERTY, "  ");
        assertEquals("postgres:18", PostgresContainerSupport.image());
    }
}
