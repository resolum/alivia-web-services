package com.resolum.alivia.assistance.domain.model.events;

public record InvitationAcceptedEvent(Long invitationId, Long assistedPersonId, Long caregiverUserId) {
}
