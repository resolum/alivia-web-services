package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body returned after a successful email verification.
 *
 * @param accountId the identifier of the verified account
 * @param status    the lifecycle status of the account
 */
@Schema(name = "VerifiedAccount", description = "Cuenta verificada")
public record VerifiedAccountResource(
        @Schema(description = "Identificador de la cuenta", example = "3f1c2a9e-8b7d-4c5e-9a1f-2b3c4d5e6f70")
        String accountId,

        @Schema(description = "Estado de la cuenta", example = "ACTIVE")
        String status
) {
}
