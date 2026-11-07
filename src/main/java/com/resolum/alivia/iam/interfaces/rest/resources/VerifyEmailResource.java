package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "VerifyEmailRequest", description = "Código de verificación recibido por correo")
public record VerifyEmailResource(
        @Schema(description = "Correo electrónico de la cuenta", example = "maria.lopez@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "Código de 6 dígitos", example = "482915", requiredMode = Schema.RequiredMode.REQUIRED)
        String code
) {
}
