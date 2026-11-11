package com.resolum.alivia.assets.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record ChangeEdgeStatusResource(@NotBlank String status) {
}
