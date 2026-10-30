package com.resolum.alivia.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Información del perfil de staff")
public record StaffProfileResource(
        @Schema(description = "Identificador único del perfil de staff", example = "1")
        Long id,

        @Schema(description = "Correo electrónico", example = "maria.staff@alivia.com")
        String emailAddress,

        @Schema(description = "URL pública de la foto de perfil", example = "https://res.cloudinary.com/alivia/image/upload/v12345/staff_avatar.jpg")
        String photoUrl,

        @Schema(description = "ID público del archivo en Cloudinary", example = "profiles/staff/def456uvw")
        String photoPublicId,

        @Schema(description = "Número telefónico", example = "51912345678")
        String phoneNumber,

        @Schema(description = "Nombres", example = "María")
        String firstName,

        @Schema(description = "Apellidos", example = "González Ruiz")
        String lastName,

        @Schema(description = "DNI (8 dígitos)", example = "45678901")
        String dni,

        @Schema(description = "Fecha de nacimiento", example = "1992-08-20")
        LocalDate birthDate
) {
}
