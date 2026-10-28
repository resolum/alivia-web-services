package com.resolum.alivia.profiles.domain.model.commands;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

public record CreatePersonProfileCommand(
        String emailAddress,
        byte[] photoData,
        String photoFileName,
        String phoneNumber,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate) {

    public CreatePersonProfileCommand {
        ProfileCommandValidator.validateCreatePerson(
                emailAddress, photoData, phoneNumber, firstName, lastName, dni, birthDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreatePersonProfileCommand that)) return false;
        return Objects.equals(emailAddress, that.emailAddress)
                && Arrays.equals(photoData, that.photoData)
                && Objects.equals(photoFileName, that.photoFileName)
                && Objects.equals(phoneNumber, that.phoneNumber)
                && Objects.equals(firstName, that.firstName)
                && Objects.equals(lastName, that.lastName)
                && Objects.equals(dni, that.dni)
                && Objects.equals(birthDate, that.birthDate);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(emailAddress, photoFileName, phoneNumber, firstName, lastName, dni, birthDate);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "CreatePersonProfileCommand["
                + "emailAddress=" + emailAddress
                + ", photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", photoFileName=" + photoFileName
                + ", phoneNumber=" + phoneNumber
                + ", firstName=" + firstName
                + ", lastName=" + lastName
                + ", dni=" + dni
                + ", birthDate=" + birthDate + "]";
    }
}
