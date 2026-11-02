package com.resolum.alivia.profiles.domain.model.commands;

import java.time.LocalDate;

public record UpdateStaffProfileCommand(
        Long profileId,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String phoneNumber,
        String photoUrl,
        String photoPublicId) {

    public UpdateStaffProfileCommand {
        ProfileCommandValidator.validateUpdatePerson(
                profileId, firstName, lastName, birthDate, phoneNumber, photoUrl, photoPublicId);
    }
}
