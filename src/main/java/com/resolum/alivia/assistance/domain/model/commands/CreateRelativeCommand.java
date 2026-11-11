package com.resolum.alivia.assistance.domain.model.commands;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

public record CreateRelativeCommand(
        Long businessId,
        String emailAddress,
        String password,
        byte[] photoData,
        String photoFileName,
        String phoneNumber,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate
) {
    public CreateRelativeCommand {
        Objects.requireNonNull(businessId, "Business ID cannot be null.");
        if (emailAddress == null || emailAddress.isBlank()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof CreateRelativeCommand other)) return false;
        return Objects.equals(businessId, other.businessId)
                && Objects.equals(emailAddress, other.emailAddress)
                && Objects.equals(password, other.password)
                && Arrays.equals(photoData, other.photoData)
                && Objects.equals(photoFileName, other.photoFileName)
                && Objects.equals(phoneNumber, other.phoneNumber)
                && Objects.equals(firstName, other.firstName)
                && Objects.equals(lastName, other.lastName)
                && Objects.equals(dni, other.dni)
                && Objects.equals(birthDate, other.birthDate);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(businessId, emailAddress, password, photoFileName, phoneNumber,
                firstName, lastName, dni, birthDate);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "CreateRelativeCommand[businessId=" + businessId
                + ", emailAddress=" + emailAddress
                + ", password=***"
                + ", photoData=" + Arrays.toString(photoData)
                + ", photoFileName=" + photoFileName
                + ", phoneNumber=" + phoneNumber
                + ", firstName=" + firstName
                + ", lastName=" + lastName
                + ", dni=" + dni
                + ", birthDate=" + birthDate + ']';
    }
}
