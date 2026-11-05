package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;

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
}
