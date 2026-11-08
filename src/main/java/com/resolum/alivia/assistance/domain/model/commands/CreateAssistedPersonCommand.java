package com.resolum.alivia.assistance.domain.model.commands;

import java.time.LocalDate;
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
        LocalDate birthDate
) {
    public CreateAssistedPersonCommand {
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
    }
}
