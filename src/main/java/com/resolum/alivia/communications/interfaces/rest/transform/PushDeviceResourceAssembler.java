package com.resolum.alivia.communications.interfaces.rest.transform;

import com.resolum.alivia.communications.domain.model.aggregates.PushDevice;
import com.resolum.alivia.communications.interfaces.rest.resources.PushDeviceResource;

public class PushDeviceResourceAssembler {

    public static PushDeviceResource toResourceFromEntity(PushDevice entity) {
        if (entity == null) return null;
        return new PushDeviceResource(
                entity.getId(),
                entity.getUserId() != null ? entity.getUserId().userId() : null,
                entity.getToken(),
                entity.getPlatform() != null ? entity.getPlatform().name() : null,
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null
        );
    }
}
