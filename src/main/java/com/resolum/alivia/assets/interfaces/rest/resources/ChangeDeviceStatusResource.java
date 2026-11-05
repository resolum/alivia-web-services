package com.resolum.alivia.assets.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record ChangeDeviceStatusResource(@NotBlank String status) {
}
