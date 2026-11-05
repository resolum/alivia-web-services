package com.resolum.alivia.assets.domain.model.commands;

/**
 * Takes a device out of the household it is assigned to. The assignment is closed, never deleted.
 *
 * @param deviceId                         device to unassign
 * @param reason                           why it leaves the household (free text)
 * @param unassignedBy                     account that unassigns it
 * @param unassignedByBusinessAdministrator whether that account administers the business (it does not have to
 *                                         belong to the household)
 */
public record UnassignDeviceCommand(Long deviceId, String reason, Long unassignedBy,
                                    boolean unassignedByBusinessAdministrator) {

    public UnassignDeviceCommand {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("Device ID must be a positive number.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason cannot be null or blank.");
        }
        if (unassignedBy == null || unassignedBy <= 0) {
            throw new IllegalArgumentException("The account that unassigns the device is required.");
        }
    }
}
