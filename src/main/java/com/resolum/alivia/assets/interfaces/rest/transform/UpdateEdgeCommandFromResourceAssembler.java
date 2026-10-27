package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.commands.UpdateEdgeCommand;
import com.resolum.alivia.assets.interfaces.rest.resources.UpdateEdgeResource;

public class UpdateEdgeCommandFromResourceAssembler {
    public static UpdateEdgeCommand toCommandFromResource (Long edgeId, UpdateEdgeResource resource){

        return new UpdateEdgeCommand(edgeId,resource.macAddress(),resource.edgeType());
    }
}
