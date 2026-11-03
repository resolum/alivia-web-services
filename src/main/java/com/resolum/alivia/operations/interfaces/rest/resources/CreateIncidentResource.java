package com.resolum.alivia.operations.interfaces.rest.resources;

import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentResource(
        @NotBlank @Size(max = 36) String householdId,
        @NotNull Long deviceId,
        @NotBlank @Size(max = 500) String description,
        @NotNull IncidentSeverity severity) {
}
