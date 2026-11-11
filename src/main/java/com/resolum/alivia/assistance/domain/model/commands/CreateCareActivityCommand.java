package com.resolum.alivia.assistance.domain.model.commands;

import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;

import java.util.Objects;

public record CreateCareActivityCommand(
        Long relativeId,
        Long assistedPersonId,
        CareActivityType type,
        String title,
        String description
) {
    public CreateCareActivityCommand {
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
        Objects.requireNonNull(assistedPersonId, "Assisted person ID cannot be null.");
        Objects.requireNonNull(type, "Type cannot be null.");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank.");
        }
    }
}
