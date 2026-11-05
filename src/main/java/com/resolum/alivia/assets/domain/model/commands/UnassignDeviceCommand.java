package com.resolum.alivia.assets.domain.model.commands;

import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;

/**
 * Takes a device out of the household it is assigned to. The assignment is closed, never deleted.
 *
 * @param deviceId     device to unassign
 * @param reason       why it leaves the household (free text)
 * @param unassignedBy account that unassigns it
 * @param authority    why that account may act: it manages the household, it administers the business, or another
 *                     context already authorized it
 */
public record UnassignDeviceCommand(Long deviceId, String reason, Long unassignedBy, CallerAuthority authority) {

    public UnassignDeviceCommand {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("Device ID must be a positive number.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason cannot be null or blank.");
        }
        if (authority == null) {
            throw new IllegalArgumentException("The caller authority is required.");
        }
        if (unassignedBy == null || unassignedBy <= 0) {
            throw new IllegalArgumentException("The account that unassigns the device is required.");
        }
    }
}
