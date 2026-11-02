package com.resolum.alivia.profiles.domain.model.commands;

import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;

import java.time.LocalDate;

public final class ProfileCommandValidator {

    private ProfileCommandValidator() {
    }

    public static void validateCreatePerson(
            String emailAddress,
            String photoUrl,
            String photoPublicId,
            String phoneNumber,
            String firstName,
            String lastName,
            String dni,
            LocalDate birthDate) {
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

    public static void validateUpdatePerson(
            Long profileId,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String phoneNumber,
            String photoUrl,
            String photoPublicId) {
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

    public static void validateBusinessFields(
            String emailAddress,
            String photoUrl,
            String photoPublicId,
            String whatsappNum,
            String ruc,
            String businessName,
            String legalName,
            String description,
            String mainActivity,
            String emergencyPhone,
            BusinessHours businessHours,
            String slogan,
            String supportEmail,
            String streetAddress,
            Double latitude,
            Double longitude,
            String placeId) {
        if (emailAddress == null || emailAddress.isBlank()) {
            throw new IllegalArgumentException("Email address cannot be null or blank");
        }
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new IllegalArgumentException("Photo URL cannot be null or blank");
        }
        if (photoPublicId == null || photoPublicId.isBlank()) {
            throw new IllegalArgumentException("Photo public ID cannot be null or blank");
        }
        if (whatsappNum == null || whatsappNum.isBlank()) {
            throw new IllegalArgumentException("Whatsapp number cannot be null or blank");
        }
        if (ruc == null || ruc.isBlank()) {
            throw new IllegalArgumentException("RUC cannot be null or blank");
        }
        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException("Business name cannot be null or blank");
        }
        if (legalName == null || legalName.isBlank()) {
            throw new IllegalArgumentException("Legal name cannot be null or blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be null or blank");
        }
        if (mainActivity == null || mainActivity.isBlank()) {
            throw new IllegalArgumentException("Main activity cannot be null or blank");
        }
        if (emergencyPhone == null || emergencyPhone.isBlank()) {
            throw new IllegalArgumentException("Emergency phone cannot be null or blank");
        }
        if (businessHours == null || businessHours.periods() == null || businessHours.periods().isEmpty()) {
            throw new IllegalArgumentException("Business hours cannot be null or empty");
        }
        if (slogan == null || slogan.isBlank()) {
            throw new IllegalArgumentException("Slogan cannot be null or blank");
        }
        if (supportEmail == null || supportEmail.isBlank()) {
            throw new IllegalArgumentException("Support email cannot be null or blank");
        }
        if (streetAddress == null || streetAddress.isBlank()) {
            throw new IllegalArgumentException("Street address cannot be null or blank");
        }
        if (latitude == null || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude == null || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("Place ID cannot be null or blank");
        }
    }
}
