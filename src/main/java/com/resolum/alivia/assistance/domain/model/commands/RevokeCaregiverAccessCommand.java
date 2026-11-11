package com.resolum.alivia.assistance.domain.model.commands;

import java.util.Objects;

public record RevokeCaregiverAccessCommand(Long caregiverId, Long relativeId) {
    public RevokeCaregiverAccessCommand {
        Objects.requireNonNull(caregiverId, "Caregiver ID cannot be null.");
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
    }
}
