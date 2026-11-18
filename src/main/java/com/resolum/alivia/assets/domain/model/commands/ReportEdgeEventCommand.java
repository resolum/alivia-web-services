package com.resolum.alivia.assets.domain.model.commands;

import com.resolum.alivia.assets.domain.model.valueobjects.EdgeEventType;

/**
 * An edge service reports something that happened in its home.
 *
 * @param edgeId     the edge that reports it (from its credential)
 * @param eventId    identifier the edge gives the event; a repeated report with the same one notifies nobody again
 * @param type       what happened
 * @param deviceId   the device involved, when there is one
 * @param batteryPct the battery left, for {@link EdgeEventType#LOW_BATTERY}
 * @param detail     a short description (the words of the person, or the cause of the failure)
 */
public record ReportEdgeEventCommand(Long edgeId, String eventId, EdgeEventType type, Long deviceId,
                                     Integer batteryPct, String detail) {
    public ReportEdgeEventCommand {
        if (edgeId == null) throw new IllegalArgumentException("The edge is required.");
        if (eventId == null || !eventId.matches("[A-Za-z0-9._:-]{1,48}")) {
            throw new IllegalArgumentException("The event id must have 1 to 48 letters, digits or . _ : -");
        }
        if (type == null) throw new IllegalArgumentException("The event type is required.");
        if (batteryPct != null && (batteryPct < 0 || batteryPct > 100)) {
            throw new IllegalArgumentException("The battery must be between 0 and 100.");
        }
        detail = detail == null || detail.isBlank() ? null : detail.strip();
        if (detail != null && detail.length() > 200) detail = detail.substring(0, 200);
    }
}
