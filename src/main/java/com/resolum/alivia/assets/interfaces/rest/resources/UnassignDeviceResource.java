package com.resolum.alivia.assets.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnassignDeviceResource(@NotBlank @Size(max = 300) String reason) {
}
