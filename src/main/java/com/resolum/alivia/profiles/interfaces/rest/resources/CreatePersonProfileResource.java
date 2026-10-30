package com.resolum.alivia.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Schema(description = "Recurso para la creación de un perfil de persona")
public record CreatePersonProfileResource(
        @Schema(description = "Correo electrónico del usuario", example = "juan.perez@example.com")
        String emailAddress,

        @Schema(description = "Foto de perfil (archivo de imagen)", type = "string", format = "binary")
        MultipartFile photo,

        @Schema(description = "Número telefónico en formato internacional sin el prefijo + (E.164)", example = "51987654321")
        String phoneNumber,

        @Schema(description = "Nombres de la persona", example = "Juan Carlos")
        String firstName,

        @Schema(description = "Apellidos de la persona", example = "Pérez Gómez")
        String lastName,

        @Schema(description = "Documento Nacional de Identidad (8 dígitos numéricos)", example = "72345678")
        String dni,

        @Schema(description = "Fecha de nacimiento (formato ISO-8601: YYYY-MM-DD)", example = "1995-06-15")
        LocalDate birthDate
) {
}
