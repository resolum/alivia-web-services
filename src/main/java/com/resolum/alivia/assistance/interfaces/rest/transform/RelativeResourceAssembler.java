package com.resolum.alivia.assistance.interfaces.rest.transform;

import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.interfaces.rest.resources.RelativeResource;

public class RelativeResourceAssembler {
    public static RelativeResource toResourceFromEntity(Relative entity) {
        return new RelativeResource(
                entity.getId(),
                entity.getUserId().userId(),
                entity.getPersonProfileId().personProfileId(),
                entity.getBusinessId().businessId()
        );
    }
}
