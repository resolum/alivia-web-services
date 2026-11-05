package com.resolum.alivia.assets.interfaces.rest.resources;

import java.time.LocalDateTime;

/** The placement of a device in a household room; {@code device.status} is {@code IN_USE} (TS-10's ASSIGNED). */
public record DeviceAssignmentResource(Long id, Long installationId, Long householdId, String roomKey,
                                       String function, LocalDateTime assignedAt, DeviceResource device) {
}
