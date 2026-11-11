package com.resolum.alivia.assistance.interfaces.rest.transform;

import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.interfaces.rest.resources.CareActivityResource;

public class CareActivityResourceAssembler {
    public static CareActivityResource toResourceFromEntity(CareActivity entity) {
        return new CareActivityResource(
                entity.getId(),
                entity.getAssistedPerson() != null ? entity.getAssistedPerson().getId() : null,
                entity.getType(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus().name()
        );
    }
}
