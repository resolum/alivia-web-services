package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeResource;

public class EdgeResourceFromEntityAssembler {
    public static EdgeResource toResourceFromEntity(Edge entity){
        return new EdgeResource(entity.getId(),entity.getBusinessId().businessId(),entity.getMacAddress().macAddress(),entity.getAssetStatus().name(),entity.getEdgeType().name());
    }
}
