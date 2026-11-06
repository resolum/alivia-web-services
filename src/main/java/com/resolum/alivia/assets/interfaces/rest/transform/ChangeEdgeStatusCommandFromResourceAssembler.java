package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.commands.ChangeEdgeStatusCommand;
import com.resolum.alivia.assets.interfaces.rest.resources.ChangeEdgeStatusResource;

public class ChangeEdgeStatusCommandFromResourceAssembler {
    public static ChangeEdgeStatusCommand toCommandFromResource(Long edgeId, ChangeEdgeStatusResource resource) {
        return new ChangeEdgeStatusCommand(edgeId, resource.status());
    }
}
