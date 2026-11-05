package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;

public interface DeviceAssignmentCommandService {

    /**
     * Assigns an {@code AVAILABLE} device to the household of an installation when the account may manage it
     * and the household's active subscription allows the device. The device stays untouched if any rule fails.
     * Assignments to the same household are serialized, so the contracted quantities cannot be exceeded by
     * two requests at once.
     *
     * @return the active assignment
     */
    DeviceAssignment handle(AssignDeviceToInstallationCommand command);

    /**
     * Closes the active assignment of a device, with the reason and the account, and leaves the device available.
     * The row stays as history. Order of the checks: device (404), access (403), assignment (409).
     * An account that cannot manage anything about the device is told it has no access, not that the device
     * is unassigned, so the state of the inventory is not revealed to it.
     *
     * @return the closed assignment
     */
    DeviceAssignment handle(UnassignDeviceCommand command);
}
