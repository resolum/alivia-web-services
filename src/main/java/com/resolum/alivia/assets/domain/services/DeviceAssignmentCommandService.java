package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToHouseholdCommand;

public interface DeviceAssignmentCommandService {

    /**
     * Assigns an {@code AVAILABLE} device to a household when the household's active subscription allows it.
     * The device stays untouched if any rule fails.
     *
     * @return the active assignment
     */
    DeviceAssignment handle(AssignDeviceToHouseholdCommand command);
}
