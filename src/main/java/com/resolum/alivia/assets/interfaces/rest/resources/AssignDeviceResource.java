package com.resolum.alivia.assets.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AssignDeviceResource(
        @NotNull @Positive Long deviceId,
        @NotBlank @Size(max = 60) String roomKey,
        @NotBlank @Size(max = 80) String function) {
}
