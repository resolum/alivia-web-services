package com.resolum.alivia.assets.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record UpdateEdgeResource(@NotBlank String macAddress, @NotBlank String edgeType) {
}
