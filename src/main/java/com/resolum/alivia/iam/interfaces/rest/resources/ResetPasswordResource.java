package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ResetPasswordRequest", description = "Código recibido por correo y nueva contraseña")
public record ResetPasswordResource(
        @Schema(description = "Correo electrónico de la cuenta", example = "maria.lopez@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String email,
        @Schema(description = "Código de 6 dígitos", example = "482915", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String code,
        @Schema(description = "Nueva contraseña", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String password
) {
}
