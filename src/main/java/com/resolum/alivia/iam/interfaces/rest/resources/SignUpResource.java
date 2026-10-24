package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body of the sign-up endpoint.
 *
 * <p>{@code fullName} and {@code phoneNumber} are the profile data of the new user.
 * The application service forwards them to the Profiles bounded context through the
 * IAM anti-corruption layer after the account is persisted.</p>
 *
 * @param fullName      the full name forwarded to the Profiles context
 * @param email         the account email address
 * @param phoneNumber   the phone number forwarded to the Profiles context
 * @param password      the plain-text password
 * @param acceptedTerms whether the user accepted the terms and conditions
 */
@Schema(name = "SignUpRequest", description = "Datos necesarios para registrar una cuenta")
public record SignUpResource(
        @Schema(description = "Nombre completo del usuario", example = "Maria Lopez",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 150)
        String fullName,

        @Schema(description = "Correo electrónico de la cuenta", example = "maria.lopez@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 254)
        String email,

        @Schema(description = "Teléfono de contacto", example = "+51987654321",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 30)
        String phoneNumber,

        @Schema(description = "Contraseña que cumple la política de seguridad",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 72)
        String password,

        @Schema(description = "Aceptación de los términos y condiciones", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @AssertTrue(message = "terms_not_accepted")
        Boolean acceptedTerms
) {
}
