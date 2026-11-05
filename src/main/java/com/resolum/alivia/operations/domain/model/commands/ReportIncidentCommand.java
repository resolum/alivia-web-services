package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.IncidentProblemType;

import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;

import java.time.Instant;

/**
 * A family reports a failure of one of the devices installed in its home, from the caregiver web, and chooses
 * when and with whom the maintenance visit happens.
 *
 * @param userId       the authenticated account of the family
 * @param deviceMac    MAC address of the installed device
 * @param occurredAt   when the failure happened, if the family knows it
 * @param visit        the two-hour block chosen for the visit
 * @param technicianId the technician free in that block the family picked
 */
public record ReportIncidentCommand(Long userId, String deviceMac, IncidentProblemType problemType, String description,
                                    Instant occurredAt, SlotId visit, Long technicianId) {
}
