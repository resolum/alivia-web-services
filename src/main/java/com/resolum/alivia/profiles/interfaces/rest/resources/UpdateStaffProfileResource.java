package com.resolum.alivia.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Schema(description = "Recurso para la actualización de un perfil de staff")
public record UpdateStaffProfileResource(
        @Schema(description = "Nombres del miembro del personal", example = "María")
        String firstName,

        @Schema(description = "Apellidos del miembro del personal", example = "González Ruiz")
        String lastName,

        @Schema(description = "Fecha de nacimiento (formato ISO-8601: YYYY-MM-DD)", example = "1992-08-20")
        LocalDate birthDate,

        @Schema(description = "Número telefónico en formato internacional sin el prefijo + (E.164)", example = "51912345678")
        String phoneNumber,

        @Schema(description = "Foto de perfil (archivo de imagen)", type = "string", format = "binary")
        MultipartFile photo
) {
}
