package com.resolum.alivia.assistance.interfaces.rest.resources;

public record CreateInvitationResource(
        Long relativeId,
        Long assistedPersonId,
        String inviteeEmail
) {}
