package com.resolum.alivia.communications.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record RegisterPushDeviceResource(
        Long userId,
        @NotBlank String token,
        String platform
) {
}
