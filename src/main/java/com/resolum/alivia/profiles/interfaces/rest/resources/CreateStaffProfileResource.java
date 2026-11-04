package com.resolum.alivia.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Schema(description = "Recurso para la creación de un perfil de personal (staff)")
public record CreateStaffProfileResource(
        @Schema(description = "Correo electrónico corporativo o de staff", example = "maria.staff@alivia.com")
        String emailAddress,

        @Schema(description = "Foto de perfil (archivo de imagen)", type = "string", format = "binary")
        MultipartFile photo,

        @Schema(description = "Número telefónico en formato internacional sin el prefijo + (E.164)", example = "51912345678")
        String phoneNumber,

        @Schema(description = "Nombres del miembro del personal", example = "María")
        String firstName,

        @Schema(description = "Apellidos del miembro del personal", example = "González Ruiz")
        String lastName,

        @Schema(description = "Documento Nacional de Identidad (8 dígitos numéricos)", example = "45678901")
        String dni,

        @Schema(description = "Fecha de nacimiento (formato ISO-8601: YYYY-MM-DD)", example = "1992-08-20")
        LocalDate birthDate
) {
}
