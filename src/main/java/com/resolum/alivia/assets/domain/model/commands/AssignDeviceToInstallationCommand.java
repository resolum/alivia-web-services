package com.resolum.alivia.assets.domain.model.commands;

/**
 * Places an available device of the business inventory in an installation, in a room and with a function.
 *
 * @param installationId      installation whose household receives the device
 * @param deviceId            device of the inventory
 * @param roomKey             room of the contracted configuration it serves
 * @param function            what the device does in that room
 * @param assignedBy          account that makes the assignment
 * @param assignedByBusinessAdministrator whether that account administers the business (it does not have to
 *                            belong to the household)
 */
public record AssignDeviceToInstallationCommand(Long installationId, Long deviceId, String roomKey, String function,
                                                Long assignedBy, boolean assignedByBusinessAdministrator) {

    public AssignDeviceToInstallationCommand {
        if (installationId == null || installationId <= 0) {
            throw new IllegalArgumentException("Installation ID must be a positive number.");
        }
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("Device ID must be a positive number.");
        }
        if (roomKey == null || roomKey.isBlank()) {
            throw new IllegalArgumentException("Room cannot be null or blank.");
        }
        if (function == null || function.isBlank()) {
            throw new IllegalArgumentException("Function cannot be null or blank.");
        }
        if (assignedBy == null || assignedBy <= 0) {
            throw new IllegalArgumentException("The account that assigns the device is required.");
        }
    }
}
