package com.resolum.alivia.assistance.domain.model.commands;

import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

public record CreateAssistedPersonCommand(
        Long relativeId,
        String emailAddress,
        byte[] photoData,
        String photoFileName,
        String phoneNumber,
        String firstName,
        String lastName,
        String dni,
        LocalDate birthDate,
        Long householdId,
        Kinship kinship,
        MobilityLevel mobilityLevel,
        String emergencyContactName,
        String emergencyContactPhone,
        Boolean consentGiven,
        Long userId
) {
    public CreateAssistedPersonCommand(
            Long relativeId,
            String emailAddress,
            byte[] photoData,
            String photoFileName,
            String phoneNumber,
            String firstName,
            String lastName,
            String dni,
            LocalDate birthDate
    ) {
        this(relativeId, emailAddress, photoData, photoFileName, phoneNumber,
                firstName, lastName, dni, birthDate, null, null, null, null, null, null, null);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof CreateAssistedPersonCommand other)) return false;
        return Objects.equals(relativeId, other.relativeId)
                && Objects.equals(emailAddress, other.emailAddress)
                && Arrays.equals(photoData, other.photoData)
                && Objects.equals(photoFileName, other.photoFileName)
                && Objects.equals(phoneNumber, other.phoneNumber)
                && Objects.equals(firstName, other.firstName)
                && Objects.equals(lastName, other.lastName)
                && Objects.equals(dni, other.dni)
                && Objects.equals(birthDate, other.birthDate)
                && Objects.equals(householdId, other.householdId)
                && kinship == other.kinship
                && mobilityLevel == other.mobilityLevel
                && Objects.equals(emergencyContactName, other.emergencyContactName)
                && Objects.equals(emergencyContactPhone, other.emergencyContactPhone)
                && Objects.equals(consentGiven, other.consentGiven)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(relativeId, emailAddress, photoFileName, phoneNumber, firstName, lastName,
                dni, birthDate, householdId, kinship, mobilityLevel, emergencyContactName,
                emergencyContactPhone, consentGiven, userId);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "CreateAssistedPersonCommand[relativeId=" + relativeId
                + ", emailAddress=" + emailAddress
                + ", photoData=" + Arrays.toString(photoData)
                + ", photoFileName=" + photoFileName
                + ", phoneNumber=" + phoneNumber
                + ", firstName=" + firstName
                + ", lastName=" + lastName
                + ", dni=" + dni
                + ", birthDate=" + birthDate
                + ", householdId=" + householdId
                + ", kinship=" + kinship
                + ", mobilityLevel=" + mobilityLevel
                + ", emergencyContactName=" + emergencyContactName
                + ", emergencyContactPhone=" + emergencyContactPhone
                + ", consentGiven=" + consentGiven
                + ", userId=" + userId + ']';
    }
}
