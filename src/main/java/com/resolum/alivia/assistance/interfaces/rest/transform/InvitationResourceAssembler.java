package com.resolum.alivia.assistance.interfaces.rest.transform;

import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.interfaces.rest.resources.InvitationResource;

public class InvitationResourceAssembler {
    public static InvitationResource toResourceFromEntity(Invitation entity) {
        return new InvitationResource(
                entity.getId(),
                entity.getAssistedPerson() != null ? entity.getAssistedPerson().getId() : null,
                entity.getInviter() != null ? entity.getInviter().getId() : null,
                entity.getInviteeEmail().email(),
                entity.getCaregiverUserId() != null ? entity.getCaregiverUserId().userId() : null,
                entity.getStatus().name(),
                entity.getExpiresAt()
        );
    }
}
