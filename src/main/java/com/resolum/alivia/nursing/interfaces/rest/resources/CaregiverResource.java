package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Caregiver linked to an assisted person of a household.
 *
 * @param userId           the caregiver user identifier
 * @param profileId        the caregiver person profile identifier
 * @param assistedPersonId the assisted person the caregiver looks after
 * @param fullName         the full name; {@code null} when it cannot be resolved
 * @param email            the email; {@code null} when it cannot be resolved
 */
@Schema(name = "Caregiver", description = "Cuidador vinculado a una persona asistida del hogar")
public record CaregiverResource(
        @Schema(description = "Identificador del usuario cuidador", example = "30")
        Long userId,
        @Schema(description = "Identificador del perfil del cuidador", example = "300")
        Long profileId,
        @Schema(description = "Identificador de la persona asistida a su cargo", example = "200")
        Long assistedPersonId,
        @Schema(description = "Nombre completo", example = "Ana Torres", nullable = true)
        String fullName,
        @Schema(description = "Correo electrónico; solo se informa a quien gestiona el hogar",
                example = "ana.torres@example.com", nullable = true)
        String email
) {
}
