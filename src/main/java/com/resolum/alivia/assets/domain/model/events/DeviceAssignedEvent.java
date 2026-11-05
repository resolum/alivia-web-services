package com.resolum.alivia.assets.domain.model.events;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;

/** A device was placed in a room of a household's installation. */
public record DeviceAssignedEvent(DeviceAssignment assignment) {
}
