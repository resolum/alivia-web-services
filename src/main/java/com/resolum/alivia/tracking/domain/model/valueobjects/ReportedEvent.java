package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Instant;
import java.util.Map;

/**
 * An event as an edge reports it. The texts are parsed and checked by the domain, so a wrong one is a rejection with
 * its reason and not an error of the request.
 */
public record ReportedEvent(String eventId, String type, Instant occurredAt, Long deviceId, String commandId,
                            String action, String result, Double confidence, Integer batteryPct, String detail,
                            Map<String, Object> attributes) {
}
