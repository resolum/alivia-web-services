package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.commands.ChangeDeviceStatusCommand;
import com.resolum.alivia.assets.interfaces.rest.resources.ChangeDeviceStatusResource;

public class ChangeDeviceStatusCommandFromResourceAssembler {
    public static ChangeDeviceStatusCommand toCommandFromResource(Long deviceId, ChangeDeviceStatusResource resource) {
        return new ChangeDeviceStatusCommand(deviceId, resource.status());
    }
}
