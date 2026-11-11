package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ChangePasswordRequest", description = "Contraseña actual y nueva contraseña")
public record ChangePasswordResource(
        @Schema(description = "Contraseña actual", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String currentPassword,
        @Schema(description = "Nueva contraseña", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String newPassword
) {
}
