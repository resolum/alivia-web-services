package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body to link a caregiver to an assisted person of a household.
 *
 * @param assistedPersonId the person profile of the assisted person
 * @param caregiverUserId  the user to link as caregiver
 */
@Schema(name = "LinkCaregiverRequest", description = "Vinculación de un cuidador con una persona asistida del hogar")
public record LinkCaregiverResource(
        @Schema(description = "Identificador de la persona asistida (id devuelto por /assisted-persons)",
                example = "200", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Positive
        Long assistedPersonId,

        @Schema(description = "Identificador del usuario que se vincula como cuidador", example = "30",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Positive
        Long caregiverUserId
) {
}
