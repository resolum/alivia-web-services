package com.resolum.alivia.assistance.interfaces.rest.transform;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.interfaces.rest.resources.AssistedPersonResource;

public class AssistedPersonResourceAssembler {
    public static AssistedPersonResource toResourceFromEntity(AssistedPerson entity) {
        return new AssistedPersonResource(
                entity.getId(),
                entity.getPersonProfileId().personProfileId(),
                entity.getRelative() != null ? entity.getRelative().getId() : null
        );
    }
}
