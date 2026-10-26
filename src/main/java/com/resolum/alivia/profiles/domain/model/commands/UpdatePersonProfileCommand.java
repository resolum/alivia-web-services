package com.resolum.alivia.profiles.domain.model.commands;

import java.time.LocalDate;

public record UpdatePersonProfileCommand(
        Long profileId,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String phoneNumber,
        String photoUrl,
        String photoPublicId) {

    public UpdatePersonProfileCommand {
        ProfileCommandValidator.validateUpdatePerson(
                profileId, firstName, lastName, birthDate, phoneNumber, photoUrl, photoPublicId);
    }
}
