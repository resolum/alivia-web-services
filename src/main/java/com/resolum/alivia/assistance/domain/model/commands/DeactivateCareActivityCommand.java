package com.resolum.alivia.assistance.domain.model.commands;

import java.util.Objects;

public record DeactivateCareActivityCommand(Long careActivityId, Long relativeId) {
    public DeactivateCareActivityCommand {
        Objects.requireNonNull(careActivityId, "Care activity ID cannot be null.");
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
    }
}
