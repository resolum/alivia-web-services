package com.resolum.alivia.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Recurso para la actualización de datos generales de un hogar")
public record UpdateHouseholdResource(
        @Schema(description = "Nuevo alias o nombre del hogar", example = "Hogar Don Alberto")
        String alias,

        @Schema(description = "Nuevo número telefónico asociado", example = "51984123456")
        String phoneNumber,

        @Schema(description = "Nuevo correo electrónico asociado", example = "contacto@alivia.pe")
        String contactEmail
) {
}
