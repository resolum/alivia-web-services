package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;

public record CreateRelativeResource(
        Long businessId,
        String emailAddress,
        String password,
        String phoneNumber,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate
) {}
