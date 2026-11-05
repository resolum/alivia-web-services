package com.resolum.alivia.assets.domain.exceptions;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;

/** The device cannot be assigned because it is not {@code AVAILABLE} (already in use, in maintenance, broken or retired). */
public class DeviceNotAvailable extends RuntimeException {
    private final AssetStatus status;

    public DeviceNotAvailable(Long deviceId, AssetStatus status) {
        super(String.format("device with id %d is %s and cannot be assigned", deviceId, status));
        this.status = status;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public boolean isAlreadyAssigned() {
        return status == AssetStatus.IN_USE;
    }
}
