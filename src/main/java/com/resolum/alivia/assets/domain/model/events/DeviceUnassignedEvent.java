package com.resolum.alivia.assets.domain.model.events;

import com.resolum.alivia.assets.domain.model.valueobjects.DevicePlacement;

/** A device was taken out of a household's installation; the assignment is closed. */
public record DeviceUnassignedEvent(DevicePlacement placement) {
}
