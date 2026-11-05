package com.resolum.alivia.assets.domain.model.events;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;

/** A device was taken out of a household's installation; the assignment is closed. */
public record DeviceUnassignedEvent(DeviceAssignment assignment) {
}
