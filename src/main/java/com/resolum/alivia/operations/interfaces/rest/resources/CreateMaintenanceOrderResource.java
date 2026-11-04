package com.resolum.alivia.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Maintenance visit for an incident, in a block returned by the availability with serviceType=MAINTENANCE. */
public record CreateMaintenanceOrderResource(@NotNull Long incidentId, @NotBlank String slotId) {
}
