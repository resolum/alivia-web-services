package com.resolum.alivia.iam.domain.model.valueobjects;

/**
 * Profile data value object that IAM forwards to the Profiles bounded context.
 *
 * @param fullName    the user's full name
 * @param phoneNumber the user's phone number
 * @param businessId  optional business ID associated with the user
 * @param familiarId  optional familiar ID associated with the user
 */
public record ProfileData(String fullName, String phoneNumber, Long businessId, Long familiarId) {

    /**
     * Convenience constructor for profile data with only name and phone.
     *
     * @param fullName    the user's full name
     * @param phoneNumber the user's phone number
     */
    public ProfileData(String fullName, String phoneNumber) {
        this(fullName, phoneNumber, null, null);
    }

    /**
     * Compact constructor validating required fields.
     */
    public ProfileData {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number is required.");
        }
    }
}
