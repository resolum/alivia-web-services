package com.resolum.alivia.profiles.interfaces.rest.resources;

import java.time.LocalDate;

public record UpdateStaffProfileResource(
        String firstName,
        String lastName,
        LocalDate birthDate,
        String phoneNumber,
        String photoUrl,
        String photoPublicId
) {
}
