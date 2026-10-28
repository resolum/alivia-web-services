package com.resolum.alivia.business.domain.model.commands;

import java.time.LocalDate;

public record CreateAdministratorCommand(
        String corporateEmail,
        String password,
        byte[] photoData,
        String photoFileName,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate,
        String phoneNumber,
        String backupPersonalEmail
) {
    public CreateAdministratorCommand {
        if (corporateEmail == null || corporateEmail.isBlank()) {
            throw new IllegalArgumentException("Corporate email cannot be null or blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or blank");
        }
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name cannot be null or blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last name cannot be null or blank");
        }
        if (dni == null || dni.isBlank()) {
            throw new IllegalArgumentException("DNI cannot be null or blank");
        }
        if (birthDate == null) {
            throw new IllegalArgumentException("Birth date cannot be null");
        }
    }

    @Override
    public String toString() {
        return "CreateAdministratorCommand[" +
                "corporateEmail='" + corporateEmail + '\'' +
                ", password='[PROTECTED]'" +
                ", photoFileName='" + photoFileName + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", dni='" + dni + '\'' +
                ", birthDate=" + birthDate +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", backupPersonalEmail='" + backupPersonalEmail + '\'' +
                ']';
    }
}
