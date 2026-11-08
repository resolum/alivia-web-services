package com.resolum.alivia.communications.interfaces.rest.resources;

public record EmailResource(
        Long id,
        String emailAddress,
        String template,
        String status,
        String sentAt,
        String failureReason,
        String createdAt
) {
}
