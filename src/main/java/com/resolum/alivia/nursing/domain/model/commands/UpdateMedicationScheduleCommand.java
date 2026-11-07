package com.resolum.alivia.nursing.domain.model.commands;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Command to update the indication or schedule of an active medication.
 */
public record UpdateMedicationScheduleCommand(
        UserId userId,
        Long medicationId,
        String dose,
        List<String> times,
        String frequency,
        LocalDate effectiveFrom,
        String indicationRef
) {
    public UpdateMedicationScheduleCommand {
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(medicationId, "Medication id cannot be null");
        if (times == null || times.isEmpty()) {
            throw new IllegalArgumentException("Times cannot be empty");
        }
        if (frequency == null || frequency.isBlank()) {
            frequency = "daily";
        }
        if (effectiveFrom == null) {
            effectiveFrom = LocalDate.now();
        }
    }
}
