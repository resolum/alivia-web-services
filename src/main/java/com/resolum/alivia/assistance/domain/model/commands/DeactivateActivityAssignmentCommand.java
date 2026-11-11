package com.resolum.alivia.assistance.domain.model.commands;

import java.util.Objects;

public record DeactivateActivityAssignmentCommand(Long careActivityId, Long assignmentId, Long relativeId) {
    public DeactivateActivityAssignmentCommand {
        Objects.requireNonNull(careActivityId, "Care activity ID cannot be null.");
        Objects.requireNonNull(assignmentId, "Assignment ID cannot be null.");
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
    }
}
