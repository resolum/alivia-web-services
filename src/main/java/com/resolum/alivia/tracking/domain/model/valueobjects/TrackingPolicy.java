package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Duration;

/**
 * The limits of what an edge may report. The defaults are the ones of the edge contract
 * ({@code docs/edge-tracking-contract.md}); the application reads them from {@code alivia.tracking.*}.
 *
 * @param maxAttributeKeys  keys of the free map of an event or of the diagnostics of a device
 * @param maxAttributeBytes size of that map
 * @param maxDetail         characters of the description of an event
 * @param clockTolerance    how far in the future an edge clock may be
 * @param syncedAfter       delay from which an event received in a batch counts as executed locally
 */
public record TrackingPolicy(int maxAttributeKeys, int maxAttributeBytes, int maxDetail, Duration clockTolerance,
                             Duration syncedAfter) {

    public TrackingPolicy {
        if (maxAttributeKeys < 1 || maxAttributeBytes < 1 || maxDetail < 1) {
            throw new IllegalArgumentException("The limits must be positive.");
        }
        if (clockTolerance == null || clockTolerance.isNegative() || syncedAfter == null || syncedAfter.isNegative()) {
            throw new IllegalArgumentException("The durations must not be negative.");
        }
    }

    public static TrackingPolicy defaults() {
        return new TrackingPolicy(20, 2048, 200, Duration.ofMinutes(5), Duration.ofSeconds(60));
    }
}
