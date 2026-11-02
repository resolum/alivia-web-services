package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body of the email verification status endpoint.
 *
 * @param accountId     the public account identifier
 * @param emailVerified whether the email address has been verified
 * @param status        the lifecycle status of the account
 */
@Schema(name = "EmailVerificationStatus", description = "Estado de verificación del correo")
public record EmailVerificationStatusResource(
        @Schema(description = "Identificador de la cuenta", example = "3f1c2a9e-8b7d-4c5e-9a1f-2b3c4d5e6f70")
        String accountId,

        @Schema(description = "Indica si el correo ya fue verificado", example = "false")
        boolean emailVerified,

        @Schema(description = "Estado de la cuenta", example = "PENDING_VERIFICATION")
        String status
) {
}
