package com.resolum.alivia.communications.interfaces.rest.transform;

import com.resolum.alivia.communications.domain.model.aggregates.Email;
import com.resolum.alivia.communications.interfaces.rest.resources.EmailResource;

public class EmailResourceAssembler {

    public static EmailResource toResourceFromEntity(Email entity) {
        if (entity == null) return null;
        return new EmailResource(
                entity.getId(),
                entity.getEmailAddress() != null ? entity.getEmailAddress().email() : null,
                entity.getTemplate() != null ? entity.getTemplate().name() : null,
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.getSentAt() != null ? entity.getSentAt().toString() : null,
                entity.getFailureReason(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null
        );
    }
}
