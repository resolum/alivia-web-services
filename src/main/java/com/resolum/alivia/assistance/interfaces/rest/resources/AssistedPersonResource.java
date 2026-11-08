package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;

public record AssistedPersonResource(
        Long id,
        Long personProfileId,
        Long relativeId,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate,
        String phoneNumber,
        String photoUrl
) {
    public AssistedPersonResource(Long id, Long personProfileId, Long relativeId) {
        this(id, personProfileId, relativeId, null, null, null, null, null, null);
    }
}
