package com.resolum.alivia.assistance.domain.model.commands;

import java.util.Objects;

public record CreateInvitationCommand(
        Long relativeId,
        Long assistedPersonId,
        String inviteeEmail
) {
    public CreateInvitationCommand {
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
        Objects.requireNonNull(assistedPersonId, "Assisted person ID cannot be null.");
        if (inviteeEmail == null || inviteeEmail.isBlank()) {
            throw new IllegalArgumentException("Invitee email is required.");
        }
    }
}
