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
        if (profileId == null || profileId <= 0) {
            throw new IllegalArgumentException("Profile ID must be greater than 0");
        }
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name cannot be null or blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last name cannot be null or blank");
        }
        if (birthDate == null) {
            throw new IllegalArgumentException("Birth date cannot be null");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or blank");
        }
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new IllegalArgumentException("Photo URL cannot be null or blank");
        }
        if (photoPublicId == null || photoPublicId.isBlank()) {
            throw new IllegalArgumentException("Photo public ID cannot be null or blank");
        }
    }
}
