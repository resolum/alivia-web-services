package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body returned after a successful registration.
 *
 * @param accountId   the identifier of the created account
 * @param maskedEmail the email address with the local part partially masked
 * @param status      the lifecycle status of the account
 */
@Schema(name = "RegisteredAccount", description = "Cuenta registrada pendiente de verificación")
public record RegisteredAccountResource(
        @Schema(description = "Identificador de la cuenta", example = "1")
        Long accountId,

        @Schema(description = "Correo enmascarado", example = "m***@example.com")
        String maskedEmail,

        @Schema(description = "Estado de la cuenta", example = "PENDING_VERIFICATION")
        String status
) {
}
