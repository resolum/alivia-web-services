package com.resolum.alivia.tracking.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * One event of the history of a device. What identifies the edge or the installation is not shown.
 *
 * @param severity {@code INFORMATIVE}, {@code PREVENTIVE}, {@code IMPORTANT} or {@code CRITICAL}
 * @param origin   {@code LIVE}, or {@code SYNCED} when it was executed without connection and reached the backend later
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceEventResource(String eventId, String type, String severity, String result, String origin,
                                  Instant occurredAt, Instant receivedAt, String commandId, String action,
                                  Double confidence, Integer batteryPct, String detail, Map<String, Object> attributes) {
}
