package com.resolum.alivia.tracking.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * One recent event of the home. Unlike the history of a device it carries the device, because the events of the whole home
 * are mixed; it has none for the ones that are not about a device.
 *
 * @param deviceId the device the event is about; absent for a request for help or a loss of connection of an edge
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecentEventResource(String eventId, String type, String severity, String result, String origin,
                                  Long deviceId, Instant occurredAt, Instant receivedAt, String commandId, String action,
                                  Double confidence, Integer batteryPct, String detail, Map<String, Object> attributes) {
}
