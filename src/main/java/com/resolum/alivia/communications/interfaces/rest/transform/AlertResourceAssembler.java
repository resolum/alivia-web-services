package com.resolum.alivia.communications.interfaces.rest.transform;

import com.resolum.alivia.communications.domain.model.aggregates.Alert;
import com.resolum.alivia.communications.interfaces.rest.resources.AlertResource;

public class AlertResourceAssembler {

    public static AlertResource toResourceFromEntity(Alert entity) {
        if (entity == null) return null;
        return new AlertResource(
                entity.getId(),
                entity.getRecipientId() != null ? entity.getRecipientId().userId() : null,
                entity.getHelpRequestId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.getSentAt() != null ? entity.getSentAt().toString() : null,
                entity.getAttempts(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null
        );
    }
}
