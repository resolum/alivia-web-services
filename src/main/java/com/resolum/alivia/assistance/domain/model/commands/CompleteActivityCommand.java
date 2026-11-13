package com.resolum.alivia.assistance.domain.model.commands;

import java.time.LocalDate;
import java.util.Objects;

public record CompleteActivityCommand(
        Long careActivityId,
        Long assignmentId,
        LocalDate date,
        Long caregiverUserId,
        String notes
) {
    public CompleteActivityCommand {
        Objects.requireNonNull(assignmentId, "Assignment ID cannot be null.");
        Objects.requireNonNull(date, "Date cannot be null.");
    }
}
