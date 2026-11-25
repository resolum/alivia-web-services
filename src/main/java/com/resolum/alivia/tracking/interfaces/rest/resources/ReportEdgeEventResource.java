package com.resolum.alivia.tracking.interfaces.rest.resources;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * What an edge reports that cannot wait.
 *
 * @param eventId    identifier the edge gives the event, reused when it resends it
 * @param type       HELP_REQUESTED, LOW_BATTERY or DEVICE_FAILURE
 * @param deviceId   the device involved, when there is one
 * @param batteryPct the battery left, for LOW_BATTERY
 * @param detail     a short description: the words of the person, or the cause of the failure
 * @param occurredAt when it happened (ISO-8601, UTC); optional, the moment of reception is used without it
 */
public record ReportEdgeEventResource(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,48}") String eventId,
        @NotBlank String type,
        Long deviceId,
        @Min(0) @Max(100) Integer batteryPct,
        @Size(max = 500) String detail,
        Instant occurredAt) {

    /** The report as the edges sent it before {@code occurredAt} existed. */
    public ReportEdgeEventResource(String eventId, String type, Long deviceId, Integer batteryPct, String detail) {
        this(eventId, type, deviceId, batteryPct, detail, null);
    }
}
