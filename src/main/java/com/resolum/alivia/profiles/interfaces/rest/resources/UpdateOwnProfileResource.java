package com.resolum.alivia.profiles.interfaces.rest.resources;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos editables del perfil propio. Los campos ausentes conservan su valor.")
public record UpdateOwnProfileResource(
        @Schema(description = "Nombres", example = "Juan Carlos")
        String firstName,

        @Schema(description = "Apellidos", example = "Perez Gomez")
        String lastName,

        @Schema(description = "Numero telefonico en formato internacional sin el prefijo + (E.164)",
                example = "51987654321")
        String phoneNumber,

        @Schema(description = "Preferencias de comunicacion como objeto JSON", example = "{\"email\":true,\"sms\":false}")
        String preferences,

        @Schema(description = "Foto de perfil (archivo de imagen)", type = "string", format = "binary")
        MultipartFile photo) {
}
