package com.resolum.alivia.profiles.interfaces.rest.resources;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Miembro de un hogar")
public record HouseholdMemberResource(
        @Schema(description = "Identificador unico de la membresia", example = "1")
        Long id,

        @Schema(description = "Identificador del usuario miembro", example = "10")
        Long userId,

        @Schema(description = "Responsabilidad en el hogar", example = "PRINCIPAL",
                allowableValues = {"PRINCIPAL", "CAREGIVER", "GUEST"})
        String responsibility,

        @Schema(description = "Estado de la membresia", example = "ACTIVE",
                allowableValues = {"ACTIVE", "REVOKED"})
        String status,

        @Schema(description = "Fecha de creacion")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de la ultima modificacion")
        LocalDateTime updatedAt,
        @Schema(description = "Nombres y apellidos del miembro, si tiene perfil de persona", example = "Lucia Salazar",
                nullable = true)
        String fullName) {
}
