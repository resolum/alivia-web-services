package com.resolum.alivia.business.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Representación del administrador de negocio")
public record AdministratorResource(
        @Schema(description = "Identificador único del administrador", example = "1")
        Long id,

        @Schema(description = "Identificador de la cuenta de usuario en IAM", example = "10")
        Long userId,

        @Schema(description = "Identificador del perfil de staff asociado en Profiles", example = "5")
        Long staffProfileId,

        @Schema(description = "Correo electrónico personal de respaldo o recuperación", example = "carlos.mendoza.personal@gmail.com")
        String backupPersonalEmail,

        @Schema(description = "Fecha y hora de registro", example = "2026-10-28T10:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Fecha y hora de última actualización", example = "2026-10-28T10:00:00")
        LocalDateTime updatedAt
) {}
