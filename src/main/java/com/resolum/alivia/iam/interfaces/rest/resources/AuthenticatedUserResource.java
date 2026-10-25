package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response body returned after a successful sign-in or session refresh.
 *
 * @param accessToken  the signed access token
 * @param refreshToken the refresh token used to renew the session
 * @param tokenType    the authorization scheme of the access token
 * @param expiresIn    seconds until the access token expires
 * @param user         the authenticated account
 * @param permissions  the permissions granted to the account
 */
@Schema(name = "AuthenticatedUser", description = "Sesión iniciada")
public record AuthenticatedUserResource(
        @Schema(description = "Token de acceso JWT")
        String accessToken,

        @Schema(description = "Token de renovación")
        String refreshToken,

        @Schema(description = "Esquema de autorización", example = "Bearer")
        String tokenType,

        @Schema(description = "Segundos de vigencia del token de acceso", example = "900")
        long expiresIn,

        @Schema(description = "Cuenta autenticada")
        AccountResource user,

        @Schema(description = "Permisos de la cuenta", example = "[\"ROLE_CAREGIVER\"]")
        List<String> permissions
) {

    /**
     * Account data included in the session response.
     *
     * @param id     the account identifier
     * @param email  the account email address
     * @param role   the access role
     * @param status the lifecycle status
     */
    @Schema(name = "AuthenticatedAccount", description = "Cuenta autenticada")
    public record AccountResource(
            @Schema(description = "Identificador de la cuenta", example = "3f1c2a9e-8b7d-4c5e-9a1f-2b3c4d5e6f70")
            String id,

            @Schema(description = "Correo electrónico", example = "maria.lopez@example.com")
            String email,

            @Schema(description = "Rol de acceso", example = "CAREGIVER")
            String role,

            @Schema(description = "Estado de la cuenta", example = "ACTIVE")
            String status
    ) {
    }
}
