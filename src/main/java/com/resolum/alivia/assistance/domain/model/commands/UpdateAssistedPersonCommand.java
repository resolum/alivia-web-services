package com.resolum.alivia.assistance.domain.model.commands;

import java.time.LocalDate;
import java.util.Objects;

public record UpdateAssistedPersonCommand(
        Long assistedPersonId,
        Long relativeId,
        byte[] photoData,
        String photoFileName,
        String phoneNumber,
        String firstName,
        String lastName,
        LocalDate birthDate
) {
    public UpdateAssistedPersonCommand {
        Objects.requireNonNull(assistedPersonId, "Assisted person ID cannot be null.");
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
    }
}
