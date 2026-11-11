package com.resolum.alivia.assistance.domain.model.commands;

import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

public record UpdateAssistedPersonCommand(
        Long assistedPersonId,
        Long relativeId,
        byte[] photoData,
        String photoFileName,
        String phoneNumber,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Long householdId,
        Kinship kinship,
        MobilityLevel mobilityLevel,
        String emergencyContactName,
        String emergencyContactPhone
) {
    public UpdateAssistedPersonCommand {
        Objects.requireNonNull(assistedPersonId, "Assisted person ID cannot be null.");
    }

    public UpdateAssistedPersonCommand(
            Long assistedPersonId,
            Long relativeId,
            byte[] photoData,
            String photoFileName,
            String phoneNumber,
            String firstName,
            String lastName,
            LocalDate birthDate
    ) {
        this(assistedPersonId, relativeId, photoData, photoFileName, phoneNumber,
                firstName, lastName, birthDate, null, null, null, null, null);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof UpdateAssistedPersonCommand other)) return false;
        return Objects.equals(assistedPersonId, other.assistedPersonId)
                && Objects.equals(relativeId, other.relativeId)
                && Arrays.equals(photoData, other.photoData)
                && Objects.equals(photoFileName, other.photoFileName)
                && Objects.equals(phoneNumber, other.phoneNumber)
                && Objects.equals(firstName, other.firstName)
                && Objects.equals(lastName, other.lastName)
                && Objects.equals(birthDate, other.birthDate)
                && Objects.equals(householdId, other.householdId)
                && kinship == other.kinship
                && mobilityLevel == other.mobilityLevel
                && Objects.equals(emergencyContactName, other.emergencyContactName)
                && Objects.equals(emergencyContactPhone, other.emergencyContactPhone);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(assistedPersonId, relativeId, photoFileName, phoneNumber, firstName,
                lastName, birthDate, householdId, kinship, mobilityLevel, emergencyContactName,
                emergencyContactPhone);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "UpdateAssistedPersonCommand[assistedPersonId=" + assistedPersonId
                + ", relativeId=" + relativeId
                + ", photoData=" + Arrays.toString(photoData)
                + ", photoFileName=" + photoFileName
                + ", phoneNumber=" + phoneNumber
                + ", firstName=" + firstName
                + ", lastName=" + lastName
                + ", birthDate=" + birthDate
                + ", householdId=" + householdId
                + ", kinship=" + kinship
                + ", mobilityLevel=" + mobilityLevel
                + ", emergencyContactName=" + emergencyContactName
                + ", emergencyContactPhone=" + emergencyContactPhone + ']';
    }
}
