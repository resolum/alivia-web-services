package com.resolum.alivia.business.domain.model.commands;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpdateAdministratorCommand that)) return false;
        return Objects.equals(administratorId, that.administratorId)
                && Arrays.equals(photoData, that.photoData)
                && Objects.equals(photoFileName, that.photoFileName)
                && Objects.equals(firstName, that.firstName)
                && Objects.equals(lastName, that.lastName)
                && Objects.equals(birthDate, that.birthDate)
                && Objects.equals(phoneNumber, that.phoneNumber)
                && Objects.equals(backupPersonalEmail, that.backupPersonalEmail);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(administratorId, photoFileName, firstName, lastName,
                birthDate, phoneNumber, backupPersonalEmail);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "UpdateAdministratorCommand["
                + "administratorId=" + administratorId
                + ", photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", photoFileName=" + photoFileName
                + ", firstName=" + firstName
                + ", lastName=" + lastName
                + ", birthDate=" + birthDate
                + ", phoneNumber=" + phoneNumber
                + ", backupPersonalEmail=" + backupPersonalEmail + "]";
    }
}
