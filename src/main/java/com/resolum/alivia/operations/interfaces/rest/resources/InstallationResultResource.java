package com.resolum.alivia.operations.interfaces.rest.resources;

import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InstallationResultResource(@NotNull InstallationResult result, @Size(max = 300) String notes) {
}
