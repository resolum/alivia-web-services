package com.resolum.alivia.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record ActivateAccountResource(@NotBlank String token, @NotBlank String password) {
}
