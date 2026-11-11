package com.resolum.alivia.assistance.domain.model.commands;

import java.util.Objects;

public record AcceptInvitationCommand(Long invitationId, Long caregiverUserId) {
    public AcceptInvitationCommand {
        Objects.requireNonNull(invitationId, "Invitation ID cannot be null.");
        Objects.requireNonNull(caregiverUserId, "Caregiver user ID cannot be null.");
    }
}
