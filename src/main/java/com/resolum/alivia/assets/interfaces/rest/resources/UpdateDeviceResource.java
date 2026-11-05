package com.resolum.alivia.assets.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record UpdateDeviceResource(@NotBlank String macAddress, @NotBlank String deviceType) {
}
