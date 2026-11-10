package com.resolum.alivia.assistance.domain.model.commands;

import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;

import java.time.LocalDate;
import java.util.Objects;

public record UpdateAssistedPersonCommand(
        Long assistedPersonId,
        Long relativeId,
        byte[] photoData,
        String photoFileName,
        String phoneNumber,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Long householdId,
        Kinship kinship,
        MobilityLevel mobilityLevel,
        String emergencyContactName,
        String emergencyContactPhone
) {
    public UpdateAssistedPersonCommand {
        Objects.requireNonNull(assistedPersonId, "Assisted person ID cannot be null.");
    }

    public UpdateAssistedPersonCommand(
            Long assistedPersonId,
            Long relativeId,
            byte[] photoData,
            String photoFileName,
            String phoneNumber,
            String firstName,
            String lastName,
            LocalDate birthDate
    ) {
        this(assistedPersonId, relativeId, photoData, photoFileName, phoneNumber,
                firstName, lastName, birthDate, null, null, null, null, null);
    }
}
