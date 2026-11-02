package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body of the email verification endpoint.
 *
 * @param token the plain-text verification token received by the user
 */
@Schema(name = "VerifyEmailRequest", description = "Token de verificación recibido por correo")
public record VerifyEmailResource(
        @Schema(description = "Token de verificación", requiredMode = Schema.RequiredMode.REQUIRED)
        String token
) {
}
