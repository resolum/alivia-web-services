package com.resolum.alivia.nursing.domain.model.commands;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Command to suspend a scheduled medication.
 */
public record SuspendMedicationScheduleCommand(
        UserId userId,
        Long medicationId,
        LocalDate from,
        String source
) {
    public SuspendMedicationScheduleCommand {
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(medicationId, "Medication id cannot be null");
        if (from == null) {
            from = LocalDate.now();
        }
        if (source == null || source.isBlank()) {
            source = "Médico tratante";
        }
    }
}
