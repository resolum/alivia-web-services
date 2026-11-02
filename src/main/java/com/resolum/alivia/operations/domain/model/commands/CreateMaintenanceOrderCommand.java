package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;

/**
 * Books a maintenance visit for an incident that needs on-site attention. {@code userId} is the caller: the
 * account that reported the incident, or an administrator ({@code administrator}).
 */
public record CreateMaintenanceOrderCommand(Long incidentId, SlotId slotId, Long userId, boolean administrator) {
}
