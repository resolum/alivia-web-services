package com.resolum.alivia.assets.domain.model.commands;

/**
 * Places an available device in a household.
 *
 * @param deviceId    device of the business inventory
 * @param householdId household that receives it
 * @param roomKey     room of the contracted configuration it serves
 * @param function    what the device does in that room
 * @param assignedBy  account that makes the assignment
 */
public record AssignDeviceToHouseholdCommand(Long deviceId, Long householdId, String roomKey, String function,
                                             Long assignedBy) {

    public AssignDeviceToHouseholdCommand {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("Device ID must be a positive number.");
        }
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household ID must be a positive number.");
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
