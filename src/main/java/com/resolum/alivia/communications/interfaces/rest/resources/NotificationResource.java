package com.resolum.alivia.communications.interfaces.rest.resources;

public record NotificationResource(
        Long id,
        Long recipientUserId,
        String type,
        String sourceId,
        String title,
        String body,
        String status,
        String readAt,
        String pushStatus,
        Integer pushAttempts,
        String createdAt
) {
}
