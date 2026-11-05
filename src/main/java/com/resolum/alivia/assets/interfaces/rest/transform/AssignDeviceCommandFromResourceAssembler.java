package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.interfaces.rest.resources.AssignDeviceResource;

public class AssignDeviceCommandFromResourceAssembler {
    public static AssignDeviceToInstallationCommand toCommandFromResource(Long installationId, Long assignedBy,
                                                                          CallerAuthority authority,
                                                                          AssignDeviceResource resource) {
        return new AssignDeviceToInstallationCommand(installationId, resource.deviceId(), resource.roomKey(),
                resource.function(), assignedBy, authority);
    }
}
