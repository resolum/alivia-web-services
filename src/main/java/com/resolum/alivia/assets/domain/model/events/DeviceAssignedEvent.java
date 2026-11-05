package com.resolum.alivia.assets.domain.model.events;

import com.resolum.alivia.assets.domain.model.valueobjects.DevicePlacement;

/** A device was placed in a room of a household's installation. */
public record DeviceAssignedEvent(DevicePlacement placement) {
}
