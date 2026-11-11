package com.resolum.alivia.assistance.domain.model.commands;

import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;

import java.util.Objects;

public record UpdateCareActivityCommand(
        Long careActivityId,
        Long relativeId,
        CareActivityType type,
        String title,
        String description
) {
    public UpdateCareActivityCommand {
        Objects.requireNonNull(careActivityId, "Care activity ID cannot be null.");
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
    }
}
