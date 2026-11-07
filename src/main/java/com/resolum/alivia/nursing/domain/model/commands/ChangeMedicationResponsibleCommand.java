package com.resolum.alivia.nursing.domain.model.commands;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Objects;

/**
 * Command to change the responsible caregiver of a scheduled medication.
 */
public record ChangeMedicationResponsibleCommand(
        UserId userId,
        Long medicationId,
        Long caregiverId
) {
    public ChangeMedicationResponsibleCommand {
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(medicationId, "Medication id cannot be null");
    }
}
