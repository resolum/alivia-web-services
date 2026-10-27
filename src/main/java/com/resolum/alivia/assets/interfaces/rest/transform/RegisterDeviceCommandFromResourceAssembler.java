package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceCommand;
import com.resolum.alivia.assets.interfaces.rest.resources.RegisterDeviceResource;

public class RegisterDeviceCommandFromResourceAssembler {
    public static RegisterDeviceCommand toCommandFromResource(RegisterDeviceResource resource, Long businessId){
        return new RegisterDeviceCommand(businessId,resource.macAddress(),resource.deviceType());
    }

}
