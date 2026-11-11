package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDateTime;

public record InvitationResource(
        Long id,
        Long assistedPersonId,
        Long inviterId,
        String inviteeEmail,
        Long caregiverUserId,
        String status,
        LocalDateTime expiresAt
) {}
