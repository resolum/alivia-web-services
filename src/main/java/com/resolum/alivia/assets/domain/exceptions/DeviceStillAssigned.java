package com.resolum.alivia.assets.domain.exceptions;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;

/**
 * A status change would leave the device {@code AVAILABLE} or {@code RETIRED} while it still has an active
 * assignment. The device has to be unassigned first, so its history is closed.
 */
public class DeviceStillAssigned extends RuntimeException {
    public DeviceStillAssigned(Long deviceId, AssetStatus target) {
        super(String.format("device with id %d is still assigned and cannot become %s", deviceId, target));
    }
}
