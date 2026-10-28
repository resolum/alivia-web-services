package com.resolum.alivia.business.domain.model.commands;

import java.time.LocalDate;

public record UpdateAdministratorCommand(
        Long administratorId,
        byte[] photoData,
        String photoFileName,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String phoneNumber,
        String backupPersonalEmail
) {
    public UpdateAdministratorCommand {
        if (administratorId == null || administratorId <= 0) {
            throw new IllegalArgumentException("Administrator ID must be a positive number");
        }
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name cannot be null or blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last name cannot be null or blank");
        }
    }
}
