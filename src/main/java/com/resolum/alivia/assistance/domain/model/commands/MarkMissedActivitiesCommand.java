package com.resolum.alivia.assistance.domain.model.commands;

import java.time.LocalDate;
import java.util.Objects;

public record MarkMissedActivitiesCommand(LocalDate closingDate) {
    public MarkMissedActivitiesCommand {
        Objects.requireNonNull(closingDate, "Closing date cannot be null.");
    }
}
