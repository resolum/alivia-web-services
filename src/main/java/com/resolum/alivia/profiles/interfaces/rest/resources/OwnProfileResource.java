package com.resolum.alivia.profiles.interfaces.rest.resources;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Perfil del usuario autenticado")
public record OwnProfileResource(
        @Schema(description = "Identificador unico del perfil", example = "1")
        Long id,

        @Schema(description = "Tipo de perfil", example = "PERSON", allowableValues = {"PERSON", "STAFF"})
        String type,

        @Schema(description = "Correo electronico (solo lectura)", example = "juan.perez@example.com")
        String emailAddress,

        @Schema(description = "URL publica de la foto de perfil")
        String photoUrl,

        @Schema(description = "ID publico del archivo en Cloudinary")
        String photoPublicId,

        @Schema(description = "Numero telefonico", example = "51987654321")
        String phoneNumber,

        @Schema(description = "Nombres", example = "Juan Carlos")
        String firstName,

        @Schema(description = "Apellidos", example = "Perez Gomez")
        String lastName,

        @Schema(description = "DNI (solo lectura)", example = "72345678")
        String dni,

        @Schema(description = "Fecha de nacimiento (solo lectura)", example = "1995-06-15")
        LocalDate birthDate,

        @Schema(description = "Preferencias de comunicacion")
        Map<String, Object> preferences,

        @Schema(description = "Fecha de la ultima modificacion")
        LocalDateTime updatedAt) {
}
