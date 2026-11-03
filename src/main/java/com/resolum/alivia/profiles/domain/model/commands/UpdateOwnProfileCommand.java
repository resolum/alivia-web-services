package com.resolum.alivia.profiles.domain.model.commands;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Command that updates the profile owned by an authenticated user.
 *
 * <p>Every field is optional so the endpoint behaves as a patch: a {@code null} value
 * keeps the stored value. An empty preferences map clears the stored preferences.</p>
 *
 * @param userId      the authenticated user identifier
 * @param firstName   the new first name, or {@code null} to keep it
 * @param lastName    the new last name, or {@code null} to keep it
 * @param phoneNumber the new phone number, or {@code null} to keep it
 * @param photoData   the new photo bytes, or {@code null} to keep it
 * @param photoFileName the original file name of the new photo
 * @param preferences the new preferences, or {@code null} to keep them
 */
public record UpdateOwnProfileCommand(
        Long userId,
        String firstName,
        String lastName,
        String phoneNumber,
        byte[] photoData,
        String photoFileName,
        Map<String, Object> preferences) {

    public UpdateOwnProfileCommand {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be greater than 0");
        }
        if (firstName != null && firstName.isBlank()) {
            throw new IllegalArgumentException("First name cannot be blank");
        }
        if (lastName != null && lastName.isBlank()) {
            throw new IllegalArgumentException("Last name cannot be blank");
        }
        if (phoneNumber != null && phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be blank");
        }
        if (photoData != null && photoData.length > 0
                && (photoFileName == null || photoFileName.isBlank())) {
            throw new IllegalArgumentException("Photo file name cannot be null or blank");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpdateOwnProfileCommand that)) return false;
        return Objects.equals(userId, that.userId)
                && Objects.equals(firstName, that.firstName)
                && Objects.equals(lastName, that.lastName)
                && Objects.equals(phoneNumber, that.phoneNumber)
                && Arrays.equals(photoData, that.photoData)
                && Objects.equals(photoFileName, that.photoFileName)
                && Objects.equals(preferences, that.preferences);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(userId, firstName, lastName, phoneNumber, photoFileName, preferences);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "UpdateOwnProfileCommand["
                + "userId=" + userId
                + ", firstName=" + firstName
                + ", lastName=" + lastName
                + ", phoneNumber=" + phoneNumber
                + ", photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", photoFileName=" + photoFileName
                + ", preferences=" + preferences + "]";
    }
}
