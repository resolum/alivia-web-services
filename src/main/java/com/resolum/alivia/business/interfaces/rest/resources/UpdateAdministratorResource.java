package com.resolum.alivia.business.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Recurso para la actualización de datos personales de un administrador")
public record UpdateAdministratorResource(
        @Schema(description = "Bytes del archivo de imagen para actualizar foto de perfil (opcional, base64)", nullable = true)
        byte[] photoData,

        @Schema(description = "Nombre original del archivo de foto de perfil", example = "admin-avatar-nuevo.png", nullable = true)
        String photoFileName,

        @NotBlank(message = "First name cannot be blank")
        @Schema(description = "Nombres del administrador", example = "Carlos Alberto")
        String firstName,

        @NotBlank(message = "Last name cannot be blank")
        @Schema(description = "Apellidos del administrador", example = "Mendoza Ruiz")
        String lastName,

        @NotNull(message = "Birth date is required")
        @Schema(description = "Fecha de nacimiento (formato ISO-8601: YYYY-MM-DD)", example = "1988-04-12")
        LocalDate birthDate,

        @Schema(description = "Número telefónico de contacto en formato E.164 sin prefijo +", example = "51976543210")
        String phoneNumber,

        @Schema(description = "Correo electrónico personal de respaldo o recuperación", example = "carlos.mendoza.personal@gmail.com")
        String backupPersonalEmail
) {}
