package com.resolum.alivia.assistance.domain.model.events;

public record InvitationAccountCreatedEvent(Long invitationId, String inviteeEmail, String passwordSetupUrl) {
}
