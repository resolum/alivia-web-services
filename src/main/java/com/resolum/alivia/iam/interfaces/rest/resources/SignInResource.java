package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body of the sign-in endpoint.
 *
 * @param email    the account email address
 * @param password the plain-text password
 */
@Schema(name = "SignInRequest", description = "Credenciales de acceso")
public record SignInResource(
        @Schema(description = "Correo electrónico de la cuenta", example = "maria.lopez@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String email,

        @Schema(description = "Contraseña", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String password
) {
}
