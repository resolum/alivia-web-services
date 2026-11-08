package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;

public record CreateAssistedPersonResource(
        Long relativeId,
        String emailAddress,
        String phoneNumber,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate
) {}
