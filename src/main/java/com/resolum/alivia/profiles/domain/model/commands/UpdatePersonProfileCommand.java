package com.resolum.alivia.profiles.domain.model.commands;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

public record UpdatePersonProfileCommand(
        Long profileId,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String phoneNumber,
        byte[] photoData,
        String photoFileName) {

    public UpdatePersonProfileCommand {
        ProfileCommandValidator.validateUpdatePerson(
                profileId, firstName, lastName, birthDate, phoneNumber, photoData);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpdatePersonProfileCommand that)) return false;
        return Objects.equals(profileId, that.profileId)
                && Objects.equals(firstName, that.firstName)
                && Objects.equals(lastName, that.lastName)
                && Objects.equals(birthDate, that.birthDate)
                && Objects.equals(phoneNumber, that.phoneNumber)
                && Arrays.equals(photoData, that.photoData)
                && Objects.equals(photoFileName, that.photoFileName);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(profileId, firstName, lastName, birthDate, phoneNumber, photoFileName);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "UpdatePersonProfileCommand["
                + "profileId=" + profileId
                + ", firstName=" + firstName
                + ", lastName=" + lastName
                + ", birthDate=" + birthDate
                + ", phoneNumber=" + phoneNumber
                + ", photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", photoFileName=" + photoFileName + "]";
    }
}
