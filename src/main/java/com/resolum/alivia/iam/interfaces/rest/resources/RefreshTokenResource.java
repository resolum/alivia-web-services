package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body of the refresh and sign-out endpoints.
 *
 * @param refreshToken the plain-text refresh token
 */
@Schema(name = "RefreshTokenRequest", description = "Token de renovación de la sesión")
public record RefreshTokenResource(
        @Schema(description = "Token de renovación", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String refreshToken
) {
}
