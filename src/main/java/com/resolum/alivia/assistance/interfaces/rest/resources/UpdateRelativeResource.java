package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;

public record UpdateRelativeResource(
        String phoneNumber,
        String firstName,
        String lastName,
        LocalDate birthDate
) {}
