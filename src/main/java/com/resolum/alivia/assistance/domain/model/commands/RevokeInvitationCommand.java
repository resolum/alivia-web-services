package com.resolum.alivia.assistance.domain.model.commands;

import java.util.Objects;

public record RevokeInvitationCommand(Long invitationId, Long relativeId) {
    public RevokeInvitationCommand {
        Objects.requireNonNull(invitationId, "Invitation ID cannot be null.");
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
    }
}
