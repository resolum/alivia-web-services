package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssistedPersonResource(
        Long id,
        Long personProfileId,
        Long relativeId,
        Long householdId,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate,
        String phoneNumber,
        String photoUrl,
        String kinship,
        String mobilityLevel,
        String emergencyContactName,
        String emergencyContactPhone,
        LocalDateTime consentGivenAt
) {
    public AssistedPersonResource(Long id, Long personProfileId, Long relativeId) {
        this(id, personProfileId, relativeId, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
