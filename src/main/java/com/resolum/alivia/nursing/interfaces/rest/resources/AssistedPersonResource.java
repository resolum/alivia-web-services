package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Assisted person of a household.
 *
 * @param id       the person profile identifier
 * @param fullName the full name; {@code null} when it cannot be resolved
 */
@Schema(name = "AssistedPerson", description = "Persona asistida de un hogar")
public record AssistedPersonResource(
        @Schema(description = "Identificador del perfil de la persona asistida", example = "200")
        Long id,
        @Schema(description = "Nombre completo", example = "Maria Lopez", nullable = true)
        String fullName
) {
}
