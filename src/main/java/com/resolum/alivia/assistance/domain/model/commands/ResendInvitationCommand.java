package com.resolum.alivia.assistance.domain.model.commands;

import java.util.Objects;

public record ResendInvitationCommand(Long invitationId, Long relativeId) {
    public ResendInvitationCommand {
        Objects.requireNonNull(invitationId, "Invitation ID cannot be null.");
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
    }
}
