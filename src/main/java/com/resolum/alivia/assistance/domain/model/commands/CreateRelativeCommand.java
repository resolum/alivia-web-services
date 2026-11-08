package com.resolum.alivia.assistance.domain.model.commands;

import java.time.LocalDate;
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
    public String toString() {
        return "CreateRelativeCommand[businessId=" + businessId + ", emailAddress=" + emailAddress + ", password=***]";
    }
}
