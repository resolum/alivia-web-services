package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ForgotPasswordRequest", description = "Solicitud de un enlace para restablecer la contraseña")
public record ForgotPasswordResource(
        @Schema(description = "Correo electrónico de la cuenta", example = "maria.lopez@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String email
) {
}
