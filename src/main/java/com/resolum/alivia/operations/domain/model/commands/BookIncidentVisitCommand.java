package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;

/** Reserves the maintenance visit the family chose in the report form: its block and technician. */
public record BookIncidentVisitCommand(Long incidentId, SlotId slotId, Long technicianId) {
}
