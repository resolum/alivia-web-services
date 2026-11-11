package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;

public record UpdateAssistedPersonResource(
        Long relativeId,
        Long householdId,
        String phoneNumber,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String kinship,
        String mobilityLevel,
        String emergencyContactName,
        String emergencyContactPhone
) {
    public UpdateAssistedPersonResource(
            Long relativeId,
            String phoneNumber,
            String firstName,
            String lastName,
            LocalDate birthDate
    ) {
        this(relativeId, null, phoneNumber, firstName, lastName, birthDate, null, null, null, null);
    }
}
