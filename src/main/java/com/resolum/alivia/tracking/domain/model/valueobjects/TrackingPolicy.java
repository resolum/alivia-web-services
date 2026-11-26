package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Duration;
import java.time.ZoneId;

/**
 * The limits of what an edge may report. The defaults are the ones of the edge contract
 * ({@code docs/edge-tracking-contract.md}); the application reads them from {@code alivia.tracking.*}.
 *
 * @param maxAttributeKeys  keys of the free map of an event or of the diagnostics of a device
 * @param maxAttributeBytes size of that map
 * @param maxDetail         characters of the description of an event
 * @param clockTolerance    how far in the future an edge clock may be
 * @param syncedAfter       delay from which an event received in a batch counts as executed locally
 * @param maxBatchSize      events an edge may send in one batch
 * @param helpDelayedAfter  how late a request for help must arrive to be told to the family as a delayed one
 * @param zone              the time zone of the homes, to say at what hour something happened
 */
public record TrackingPolicy(int maxAttributeKeys, int maxAttributeBytes, int maxDetail, Duration clockTolerance,
                             Duration syncedAfter, int maxBatchSize, Duration helpDelayedAfter, ZoneId zone) {

    public static final int DEFAULT_MAX_BATCH_SIZE = 100;
    public static final Duration DEFAULT_HELP_DELAYED_AFTER = Duration.ofMinutes(5);
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Lima");

    /** A policy with the batch size of the edge contract. */
    public TrackingPolicy(int maxAttributeKeys, int maxAttributeBytes, int maxDetail, Duration clockTolerance,
                          Duration syncedAfter) {
        this(maxAttributeKeys, maxAttributeBytes, maxDetail, clockTolerance, syncedAfter, DEFAULT_MAX_BATCH_SIZE);
    }

    /** A policy with the default for a delayed request for help (5 minutes) and for the time zone (Lima). */
    public TrackingPolicy(int maxAttributeKeys, int maxAttributeBytes, int maxDetail, Duration clockTolerance,
                          Duration syncedAfter, int maxBatchSize) {
        this(maxAttributeKeys, maxAttributeBytes, maxDetail, clockTolerance, syncedAfter, maxBatchSize,
                DEFAULT_HELP_DELAYED_AFTER, DEFAULT_ZONE);
    }

    public TrackingPolicy {
        if (maxAttributeKeys < 1 || maxAttributeBytes < 1 || maxDetail < 1 || maxBatchSize < 1) {
            throw new IllegalArgumentException("The limits must be positive.");
        }
        if (clockTolerance == null || clockTolerance.isNegative() || syncedAfter == null || syncedAfter.isNegative()) {
            throw new IllegalArgumentException("The durations must not be negative.");
        }
        if (helpDelayedAfter == null || helpDelayedAfter.isNegative()) {
            throw new IllegalArgumentException("The delay of a request for help must not be negative.");
        }
        if (zone == null) {
            throw new IllegalArgumentException("The time zone is required.");
        }
    }

    public static TrackingPolicy defaults() {
        return new TrackingPolicy(20, 2048, 200, Duration.ofMinutes(5), Duration.ofSeconds(60),
                DEFAULT_MAX_BATCH_SIZE, DEFAULT_HELP_DELAYED_AFTER, DEFAULT_ZONE);
    }
}
