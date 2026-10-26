package com.resolum.alivia.profiles.interfaces.rest.resources;

import java.time.LocalDate;

public record PersonProfileResource(
        Long id,
        String emailAddress,
        String photoUrl,
        String photoPublicId,
        String phoneNumber,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate) {
}
