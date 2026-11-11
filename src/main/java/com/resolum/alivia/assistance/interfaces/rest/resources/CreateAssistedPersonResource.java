package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;

public record CreateAssistedPersonResource(
        Long relativeId,
        Long householdId,
        String emailAddress,
        String phoneNumber,
        String firstName,
        String lastName,
        String dni,
        String documentNumber,
        String documentType,
        LocalDate birthDate,
        String kinship,
        String mobilityLevel,
        String emergencyContactName,
        String emergencyContactPhone,
        Boolean consentGiven
) {
    public CreateAssistedPersonResource(
            Long relativeId,
            String emailAddress,
            String phoneNumber,
            String firstName,
            String lastName,
            String dni,
            LocalDate birthDate
    ) {
        this(relativeId, null, emailAddress, phoneNumber, firstName, lastName, dni, null, null, birthDate, null, null, null, null, null);
    }

    public String effectiveDni() {
        if (dni != null && !dni.isBlank()) {
            return dni;
        }
        return documentNumber;
    }
}
