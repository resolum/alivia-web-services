package com.resolum.alivia.business.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Representación del negocio en el sistema")
public record BusinessResource(
        @Schema(description = "Identificador único del negocio", example = "1")
        Long id,

        @Schema(description = "Identificador del perfil comercial asociado en Profiles", example = "1")
        Long businessProfileId,

        @Schema(description = "Identificador del administrador asignado", example = "1")
        Long administratorId,

        @Schema(description = "Fecha y hora de registro", example = "2026-10-28T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "Fecha y hora de última actualización", example = "2026-10-28T10:15:30")
        LocalDateTime updatedAt
) {}
