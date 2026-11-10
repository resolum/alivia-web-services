package com.resolum.alivia.assistance.domain.model.commands;

import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;

import java.time.LocalDate;

public record CreateAssistedPersonCommand(
        Long relativeId,
        String emailAddress,
        byte[] photoData,
        String photoFileName,
        String phoneNumber,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate,
        Long householdId,
        Kinship kinship,
        MobilityLevel mobilityLevel,
        String emergencyContactName,
        String emergencyContactPhone,
        Boolean consentGiven,
        Long userId
) {
    public CreateAssistedPersonCommand(
            Long relativeId,
            String emailAddress,
            byte[] photoData,
            String photoFileName,
            String phoneNumber,
            String firstName,
            String lastName,
            String dni,
            LocalDate birthDate
    ) {
        this(relativeId, emailAddress, photoData, photoFileName, phoneNumber,
                firstName, lastName, dni, birthDate, null, null, null, null, null, null, null);
    }
}
