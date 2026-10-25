package com.resolum.alivia.profiles.interfaces.rest.resources;

import java.time.LocalDate;

public record StaffProfileResource(
        Long id,
        String emailAddress,
        String photoUrl,
        String photoPublicId,
        String phoneNumber,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate
) {
}
