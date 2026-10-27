package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.commands.UpdateDeviceCommand;
import com.resolum.alivia.assets.interfaces.rest.resources.UpdateDeviceResource;

public class UpdateDeviceCommandFromResourceAssembler {
    public static UpdateDeviceCommand toCommandFromResource( Long  deviceId,UpdateDeviceResource resource){
        return new UpdateDeviceCommand( deviceId,resource.macAddress(),resource.edgeType());
    }
}
