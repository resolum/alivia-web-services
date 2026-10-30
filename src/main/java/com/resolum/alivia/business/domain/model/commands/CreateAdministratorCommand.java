package com.resolum.alivia.business.domain.model.commands;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateAdministratorCommand that)) return false;
        return Objects.equals(corporateEmail, that.corporateEmail)
                && Objects.equals(password, that.password)
                && Arrays.equals(photoData, that.photoData)
                && Objects.equals(photoFileName, that.photoFileName)
                && Objects.equals(firstName, that.firstName)
                && Objects.equals(lastName, that.lastName)
                && Objects.equals(dni, that.dni)
                && Objects.equals(birthDate, that.birthDate)
                && Objects.equals(phoneNumber, that.phoneNumber)
                && Objects.equals(backupPersonalEmail, that.backupPersonalEmail);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(corporateEmail, password, photoFileName, firstName, lastName,
                dni, birthDate, phoneNumber, backupPersonalEmail);
        return 31 * result + Arrays.hashCode(photoData);
    }

    /**
     * Credentials are intentionally left out so they never end up in logs.
     */
    @Override
    public String toString() {
        return "CreateAdministratorCommand["
                + "corporateEmail=" + corporateEmail
                + ", photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", photoFileName=" + photoFileName
                + ", firstName=" + firstName
                + ", lastName=" + lastName
                + ", dni=" + dni
                + ", birthDate=" + birthDate
                + ", phoneNumber=" + phoneNumber
                + ", backupPersonalEmail=" + backupPersonalEmail + "]";
    }
}
