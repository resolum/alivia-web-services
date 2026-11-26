package com.resolum.alivia.tracking.domain.exceptions;

/**
 * The device does not exist, is not placed in a home, or the user may not read that home. One exception for all of them,
 * so the answer does not tell which devices exist.
 */
public class DeviceNotAccessible extends RuntimeException {
    public DeviceNotAccessible(Long deviceId) {
        super("The device " + deviceId + " was not found.");
    }
}
