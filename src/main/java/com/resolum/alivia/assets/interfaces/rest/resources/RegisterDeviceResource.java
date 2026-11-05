package com.resolum.alivia.assets.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDeviceResource(
        @NotBlank String macAddress,
        @NotBlank @Size(max = 80) String alias,
        @NotBlank String deviceType) {
}
