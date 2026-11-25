package com.resolum.alivia.tracking.infrastructure.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduled tasks for the watcher of the edge links. Another context does the same for its own tasks; saying
 * it here as well means the watcher keeps running if that one changes. Enabling it twice is harmless.
 */
@Configuration
@EnableScheduling
public class TrackingSchedulingConfiguration {
}
