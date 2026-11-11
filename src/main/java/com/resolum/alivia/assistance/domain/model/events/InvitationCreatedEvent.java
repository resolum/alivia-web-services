package com.resolum.alivia.assistance.domain.model.events;

import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;

public class InvitationCreatedEvent {
    private final Invitation invitation;

    public InvitationCreatedEvent(Invitation invitation) {
        this.invitation = invitation;
    }

    public Invitation getInvitation() {
        return invitation;
    }

    public Long getInvitationId() {
        return invitation != null ? invitation.getId() : null;
    }
}
