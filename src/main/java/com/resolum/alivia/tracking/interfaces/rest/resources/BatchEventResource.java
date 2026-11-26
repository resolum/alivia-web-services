package com.resolum.alivia.tracking.interfaces.rest.resources;

import java.time.Instant;
import java.util.Map;

/**
 * One event of a batch, as the edge reports it. Every field is optional here: what is missing or wrong rejects that event
 * with its reason and does not spoil the others.
 *
 * @param eventId    identifier the edge gives the event, reused whenever it resends it
 * @param type       the type of the event
 * @param occurredAt when it happened (ISO-8601, UTC), as the edge saw it
 * @param deviceId   the device involved, when there is one
 * @param commandId  joins the request, the result and the rejection of one command
 * @param action     {@code open}, {@code close}, {@code on} or {@code off}
 * @param result     how a command ended, or why it was not run
 * @param confidence confidence of the voice model, 0 to 1
 * @param batteryPct the battery left, 0 to 100
 * @param detail     a short description
 * @param attributes what varies from one device to another (scalar values)
 */
public record BatchEventResource(String eventId, String type, Instant occurredAt, Long deviceId, String commandId,
                                 String action, String result, Double confidence, Integer batteryPct, String detail,
                                 Map<String, Object> attributes) {
}
