package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.interfaces.rest.resources.DeviceAssignmentResource;

public class DeviceAssignmentResourceFromEntityAssembler {
    public static DeviceAssignmentResource toResourceFromEntity(DeviceAssignment entity) {
        return new DeviceAssignmentResource(entity.getId(), entity.getInstallationId(), entity.getHouseholdId(),
                entity.getRoomKey(), entity.getFunction(), entity.getAssignedAt(),
                DeviceResourceFromEntityAssembler.toResourceFromEntity(entity.getDevice()));
    }
}
