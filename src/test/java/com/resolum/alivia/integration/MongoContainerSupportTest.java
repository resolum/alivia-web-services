package com.resolum.alivia.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the tests against a real MongoDB decide to run, and which MongoDB they use. No Docker needed. */
class MongoContainerSupportTest {

    @AfterEach
    void clearSettings() {
        System.clearProperty(PostgresContainerSupport.REQUIRE_DOCKER);
        System.clearProperty(MongoContainerSupport.IMAGE_PROPERTY);
    }

    @Test
    void theTestsRunWhenDockerIsPresentAndAreSkippedWhenItIsNot() {
        assertTrue(MongoContainerSupport.run(true, false));
        assertFalse(MongoContainerSupport.run(false, false));
    }

    @Test
    void whenDockerIsRequiredTheTestsAlwaysRunSoAMissingDockerFailsThemInsteadOfHidingThem() {
        assertTrue(MongoContainerSupport.run(false, true));
        assertTrue(MongoContainerSupport.run(true, true));
        System.setProperty(PostgresContainerSupport.REQUIRE_DOCKER, "true");
        assertTrue(MongoContainerSupport.dockerAvailable());
    }

    @Test
    void theImageIsTheMajorVersionOfComposeAndCanBeChanged() {
        assertEquals("mongo:8.0", MongoContainerSupport.image());
        System.setProperty(MongoContainerSupport.IMAGE_PROPERTY, " mongo:7.0 ");
        assertEquals("mongo:7.0", MongoContainerSupport.image());
        System.setProperty(MongoContainerSupport.IMAGE_PROPERTY, "  ");
        assertEquals("mongo:8.0", MongoContainerSupport.image());
    }
}
