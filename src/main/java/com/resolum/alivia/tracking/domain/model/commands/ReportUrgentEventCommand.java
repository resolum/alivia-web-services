package com.resolum.alivia.tracking.domain.model.commands;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;

import java.time.Instant;

/**
 * An edge reports something that cannot wait: a request for help, a device that failed or a battery that runs out.
 *
 * @param edgeId     the edge that reports it (from its credential)
 * @param eventId    identifier the edge gives the event; a repeated report with the same one notifies nobody again
 * @param type       what happened; one of the urgent types
 * @param deviceId   the device involved, when there is one
 * @param batteryPct the battery left, for a low battery
 * @param detail     a short description (the words of the person, or the cause of the failure)
 * @param occurredAt when it happened, if the edge says; the moment of reception otherwise
 */
public record ReportUrgentEventCommand(Long edgeId, String eventId, EventType type, Long deviceId, Integer batteryPct,
                                       String detail, Instant occurredAt) {

    public ReportUrgentEventCommand {
        if (edgeId == null) throw new IllegalArgumentException("The edge is required.");
        if (eventId == null || !DeviceEvent.EVENT_ID.matcher(eventId).matches()) {
            throw new IllegalArgumentException("The event id must have 1 to 48 letters, digits or . _ : -");
        }
        if (type == null || !type.isUrgent()) {
            throw new IllegalArgumentException("The event type must be HELP_REQUESTED, LOW_BATTERY or DEVICE_FAILURE.");
        }
        if (batteryPct != null && (batteryPct < 0 || batteryPct > 100)) {
            throw new IllegalArgumentException("The battery must be between 0 and 100.");
        }
        detail = detail == null || detail.isBlank() ? null : detail.strip();
    }
}
