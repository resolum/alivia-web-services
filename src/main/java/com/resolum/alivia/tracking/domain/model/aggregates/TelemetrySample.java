package com.resolum.alivia.tracking.domain.model.aggregates;

import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;

import java.time.Instant;

/**
 * A reading of the diagnostics of a device (signal, free memory, uptime…): high volume and low value, kept apart from the
 * events and only for a while.
 *
 * @param deviceId    the device
 * @param edgeId      the edge that reported it
 * @param householdId the household of its installation
 * @param sampledAt   when the edge observed it
 * @param metrics     the readings
 * @param expiresAt   from when the reading may be deleted; {@code null} while no retention is configured, which keeps it
 */
public record TelemetrySample(Long deviceId, Long edgeId, Long householdId, Instant sampledAt, EventAttributes metrics,
                              Instant expiresAt) {
}
