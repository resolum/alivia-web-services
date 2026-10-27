package com.resolum.alivia.iam.domain.model.valueobjects;

import java.time.LocalDate;

/**
 * Profile data value object that IAM forwards to the Profiles bounded context.
 *
 * @param firstName     the user's first name
 * @param lastName      the user's last name
 * @param phoneNumber   the user's phone number
 * @param businessId    optional business ID associated with the user
 * @param familiarId    optional familiar ID associated with the user
 * @param dni           the national identity document number
 * @param birthDate     the birth date
 * @param photo         the optional photo content
 * @param photoFileName the original file name of the photo
 */
public record ProfileData(String firstName, String lastName, String phoneNumber, Long businessId, Long familiarId,
                          String dni, LocalDate birthDate, byte[] photo,
                          String photoFileName) {

    /**
     * Convenience constructor for profile data with only name and phone.
     *
     * @param firstName   the user's first name
     * @param lastName    the user's last name
     * @param phoneNumber the user's phone number
     */
    public ProfileData(String firstName, String lastName, String phoneNumber) {
        this(firstName, lastName, phoneNumber, null, null);
    }

    /**
     * Convenience constructor for profile data without identity document and photo.
     *
     * @param firstName   the user's first name
     * @param lastName    the user's last name
     * @param phoneNumber the user's phone number
     * @param businessId  optional business ID associated with the user
     * @param familiarId  optional familiar ID associated with the user
     */
    public ProfileData(String firstName, String lastName, String phoneNumber, Long businessId, Long familiarId) {
        this(firstName, lastName, phoneNumber, businessId, familiarId, null, null, null, null);
    }

    /**
     * Compact constructor validating required fields.
     */
    public ProfileData {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name is required.");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last name is required.");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number is required.");
        }
    }
}
