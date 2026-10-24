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
        @Schema(description = "Identificador de la cuenta", example = "1")
        Long accountId,

        @Schema(description = "Estado de la cuenta", example = "ACTIVE")
        String status
) {
}
