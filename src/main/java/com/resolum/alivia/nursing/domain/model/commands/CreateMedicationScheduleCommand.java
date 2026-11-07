package com.resolum.alivia.nursing.domain.model.commands;

import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Command to schedule a new medication for an assisted person.
 */
public record CreateMedicationScheduleCommand(
        UserId userId,
        PersonProfileId assistedPersonProfileId,
        String medication,
        String dose,
        List<String> times,
        String frequency,
        LocalDate startDate,
        LocalDate endDate,
        Long caregiverId,
        String indicationRef
) {
    public CreateMedicationScheduleCommand {
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(assistedPersonProfileId, "Assisted person profile id cannot be null");
        Objects.requireNonNull(medication, "Medication cannot be null");
        if (medication.isBlank()) {
            throw new IllegalArgumentException("Medication cannot be blank");
        }
        if (times == null || times.isEmpty()) {
            throw new IllegalArgumentException("Times cannot be empty");
        }
        if (frequency == null || frequency.isBlank()) {
            frequency = "daily";
        }
        if (startDate == null) {
            startDate = LocalDate.now();
        }
    }
}
