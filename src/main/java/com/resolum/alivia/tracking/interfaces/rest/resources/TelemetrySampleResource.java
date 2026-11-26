package com.resolum.alivia.tracking.interfaces.rest.resources;

import java.time.Instant;
import java.util.Map;

/**
 * One diagnostics reading of a device.
 *
 * @param edgeId    the edge that reported it
 * @param sampledAt when the edge observed it
 * @param metrics   the readings (signal, free memory…)
 */
public record TelemetrySampleResource(Long edgeId, Instant sampledAt, Map<String, Object> metrics) {
}
