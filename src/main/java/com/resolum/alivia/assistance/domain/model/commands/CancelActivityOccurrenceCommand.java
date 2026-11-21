package com.resolum.alivia.assistance.domain.model.commands;

import java.time.LocalDate;
import java.util.Objects;

public record CancelActivityOccurrenceCommand(
        Long careActivityId,
        Long assignmentId,
        LocalDate date,
        Long relativeId
) {
    public CancelActivityOccurrenceCommand {
        Objects.requireNonNull(assignmentId, "Assignment ID cannot be null.");
        Objects.requireNonNull(date, "Date cannot be null.");
    }
}
