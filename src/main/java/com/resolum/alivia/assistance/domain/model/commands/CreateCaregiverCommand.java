package com.resolum.alivia.assistance.domain.model.commands;

import java.util.Objects;

public record CreateCaregiverCommand(Long assistedPersonId, Long caregiverUserId) {
    public CreateCaregiverCommand {
        Objects.requireNonNull(assistedPersonId, "Assisted person ID cannot be null.");
        Objects.requireNonNull(caregiverUserId, "Caregiver user ID cannot be null.");
    }
}
