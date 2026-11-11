package com.resolum.alivia.assistance.interfaces.rest.transform;

import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.interfaces.rest.resources.CaregiverResource;

public class CaregiverResourceAssembler {
    public static CaregiverResource toResourceFromEntity(Caregiver entity) {
        return new CaregiverResource(
                entity.getId(),
                entity.getUserId().userId(),
                entity.getAssistedPerson() != null ? entity.getAssistedPerson().getId() : null,
                entity.getStatus().name()
        );
    }
}
