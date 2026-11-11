package com.resolum.alivia.assets.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AcknowledgeEdgeVersionResource(
        @Schema(description = "Mayor versión de la tabla que el edge ya aplicó", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero Long appliedVersion) {
}
