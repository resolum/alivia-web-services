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
        if (emailAddress == null || emailAddress.isBlank()) {
            throw new IllegalArgumentException("Email address cannot be null or blank");
        }
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new IllegalArgumentException("Photo URL cannot be null or blank");
        }
        if (photoPublicId == null || photoPublicId.isBlank()) {
            throw new IllegalArgumentException("Photo public ID cannot be null or blank");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or blank");
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
}
