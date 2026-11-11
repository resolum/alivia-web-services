package com.resolum.alivia.assistance.domain.model.commands;

import java.util.Objects;

public record ReassignCaregiverActivitiesCommand(Long caregiverId, Long assistedPersonId) {
    public ReassignCaregiverActivitiesCommand {
        Objects.requireNonNull(caregiverId, "Caregiver ID cannot be null.");
        Objects.requireNonNull(assistedPersonId, "Assisted person ID cannot be null.");
    }
}
