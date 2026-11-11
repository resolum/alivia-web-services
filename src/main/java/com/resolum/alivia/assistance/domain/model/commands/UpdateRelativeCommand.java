package com.resolum.alivia.assistance.domain.model.commands;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

public record UpdateRelativeCommand(
        Long relativeId,
        byte[] photoData,
        String photoFileName,
        String phoneNumber,
        String firstName,
        String lastName,
        LocalDate birthDate
) {
    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof UpdateRelativeCommand other)) return false;
        return Objects.equals(relativeId, other.relativeId)
                && Arrays.equals(photoData, other.photoData)
                && Objects.equals(photoFileName, other.photoFileName)
                && Objects.equals(phoneNumber, other.phoneNumber)
                && Objects.equals(firstName, other.firstName)
                && Objects.equals(lastName, other.lastName)
                && Objects.equals(birthDate, other.birthDate);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(relativeId, photoFileName, phoneNumber, firstName, lastName, birthDate);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "UpdateRelativeCommand[relativeId=" + relativeId
                + ", photoData=" + Arrays.toString(photoData)
                + ", photoFileName=" + photoFileName
                + ", phoneNumber=" + phoneNumber
                + ", firstName=" + firstName
                + ", lastName=" + lastName
                + ", birthDate=" + birthDate + ']';
    }
}
