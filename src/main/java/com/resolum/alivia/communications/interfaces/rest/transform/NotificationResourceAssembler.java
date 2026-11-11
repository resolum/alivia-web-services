package com.resolum.alivia.communications.interfaces.rest.transform;

import com.resolum.alivia.communications.domain.model.aggregates.Notification;
import com.resolum.alivia.communications.interfaces.rest.resources.NotificationResource;

public class NotificationResourceAssembler {

    public static NotificationResource toResourceFromEntity(Notification entity) {
        if (entity == null) return null;
        return new NotificationResource(
                entity.getId(),
                entity.getRecipientId() != null ? entity.getRecipientId().userId() : null,
                entity.getType() != null ? entity.getType().name() : null,
                entity.getSourceId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.getReadAt() != null ? entity.getReadAt().toString() : null,
                entity.getPushStatus() != null ? entity.getPushStatus().name() : null,
                entity.getPushAttempts(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null
        );
    }
}
