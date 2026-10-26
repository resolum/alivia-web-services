package com.resolum.alivia.profiles.domain.model.commands;

import java.time.LocalDate;

public record CreatePersonProfileCommand(
        String emailAddress,
        String photoUrl,
        String photoPublicId,
        String phoneNumber,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate) {

    public CreatePersonProfileCommand {
        ProfileCommandValidator.validateCreatePerson(
                emailAddress, photoUrl, photoPublicId, phoneNumber, firstName, lastName, dni, birthDate);
    }
}
