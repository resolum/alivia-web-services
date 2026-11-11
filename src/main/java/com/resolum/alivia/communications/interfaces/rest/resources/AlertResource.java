package com.resolum.alivia.communications.interfaces.rest.resources;

public record AlertResource(
        Long id,
        Long recipientUserId,
        String helpRequestId,
        String title,
        String body,
        String status,
        String sentAt,
        Integer attempts,
        String createdAt
) {
}
