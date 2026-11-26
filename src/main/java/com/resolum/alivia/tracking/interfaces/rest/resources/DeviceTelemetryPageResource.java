package com.resolum.alivia.tracking.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;

/**
 * A page of the readings of a device, newest first.
 *
 * @param from the start of the period, included
 * @param to   the end of the period, excluded
 * @param page the number of the page, from 0
 */
public record DeviceTelemetryPageResource(Long deviceId, Instant from, Instant to, int page, int size,
                                          long totalElements, int totalPages, List<TelemetrySampleResource> items) {
}
