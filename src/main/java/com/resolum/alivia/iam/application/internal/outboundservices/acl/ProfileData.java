package com.resolum.alivia.iam.application.internal.outboundservices.acl;

/**
 * Profile data that IAM forwards to the Profiles bounded context when an account is
 * registered.
 *
 * <p>It is an anti-corruption boundary object: it carries only the data Profiles needs
 * and keeps the IAM aggregate out of the Profiles contract.</p>
 *
 * @param fullName    the user's full name
 * @param phoneNumber the user's phone number
 */
public record ProfileData(String fullName, String phoneNumber) {

    /**
     * Creates the profile data.
     *
     * @param fullName    the user's full name
     * @param phoneNumber the user's phone number
     * @throws IllegalArgumentException if any value is null or blank
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
