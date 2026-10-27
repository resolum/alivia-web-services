package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.commands.RegisterEdgeCommand;
import com.resolum.alivia.assets.interfaces.rest.resources.RegisterEdgeResource;

public class RegisterEdgeCommandFromResourceAssembler {
    public static RegisterEdgeCommand toCommandFromResource(RegisterEdgeResource resource, Long businessId){
        return  new RegisterEdgeCommand(businessId,resource.macAddress(),resource.edgeType());
    }
}
