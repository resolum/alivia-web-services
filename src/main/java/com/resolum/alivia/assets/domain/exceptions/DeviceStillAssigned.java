package com.resolum.alivia.assets.domain.exceptions;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;

/**
 * An operation would contradict the active assignment of the device: leaving it {@code AVAILABLE} or
 * {@code RETIRED}, or changing its type (which would skip the plan compatibility checked when it was assigned).
 * The device has to be unassigned first, so its history is closed.
 */
public class DeviceStillAssigned extends RuntimeException {
    public DeviceStillAssigned(Long deviceId, AssetStatus target) {
        super(String.format("device with id %d is still assigned and cannot become %s", deviceId, target));
    }

    private DeviceStillAssigned(String message) {
        super(message);
    }

    public static DeviceStillAssigned typeChange(Long deviceId) {
        return new DeviceStillAssigned(String.format(
                "device with id %d is still assigned and its type cannot change; unassign it first", deviceId));
    }
}
