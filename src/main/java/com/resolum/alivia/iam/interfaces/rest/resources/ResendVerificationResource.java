package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body of the resend-verification endpoint.
 *
 * @param email the account email address
 */
@Schema(name = "ResendVerificationRequest", description = "Solicitud de un nuevo enlace de verificación")
public record ResendVerificationResource(
        @Schema(description = "Correo electrónico de la cuenta", example = "maria.lopez@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String email
) {
}
