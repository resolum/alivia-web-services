package com.resolum.alivia.assets.domain.exceptions;

/** The device has no active assignment, so there is nothing to unassign. */
public class DeviceNotAssigned extends RuntimeException {
    public DeviceNotAssigned(Long deviceId) {
        super(String.format("device with id %d is not assigned", deviceId));
    }
}
