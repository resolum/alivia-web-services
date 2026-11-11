package com.resolum.alivia.profiles.interfaces.rest.resources;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Perfil del hogar y sus miembros")
public record HouseholdResource(
        @Schema(description = "Identificador unico del hogar", example = "1")
        Long id,

        @Schema(description = "Alias del hogar", example = "Casa")
        String alias,

        @Schema(description = "Direccion completa y formateada")
        String formattedAddress,

        @Schema(description = "Latitud geografica")
        Double latitude,

        @Schema(description = "Longitud geografica")
        Double longitude,

        @Schema(description = "Identificador de Google Places")
        String placeId,

        @Schema(description = "Telefono de contacto", example = "51987654321")
        String contactPhone,

        @Schema(description = "Correo de contacto", example = "hogar@alivia.com")
        String contactEmail,

        @Schema(description = "Miembros del hogar")
        List<HouseholdMemberResource> members,

        @Schema(description = "Fecha de creacion")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de la ultima modificacion")
        LocalDateTime updatedAt,

        @Schema(description = "Identificador del usuario responsable de la cuenta", example = "1")
        Long responsibleAccountId) {
}
