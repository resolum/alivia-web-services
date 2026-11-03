package com.resolum.alivia.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Información del perfil de persona")
public record PersonProfileResource(
        @Schema(description = "Identificador único del perfil", example = "1")
        Long id,

        @Schema(description = "Correo electrónico", example = "juan.perez@example.com")
        String emailAddress,

        @Schema(description = "URL pública de la foto de perfil", example = "https://res.cloudinary.com/alivia/image/upload/v12345/avatar.jpg")
        String photoUrl,

        @Schema(description = "ID público del archivo en Cloudinary", example = "profiles/avatars/abc123xyz")
        String photoPublicId,

        @Schema(description = "Número telefónico", example = "51987654321")
        String phoneNumber,

        @Schema(description = "Nombres", example = "Juan Carlos")
        String firstName,

        @Schema(description = "Apellidos", example = "Pérez Gómez")
        String lastName,

        @Schema(description = "DNI (8 dígitos)", example = "72345678")
        String dni,

        @Schema(description = "Fecha de nacimiento", example = "1995-06-15")
        LocalDate birthDate) {
}
