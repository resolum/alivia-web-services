package com.resolum.alivia.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Recurso para la creación de un nuevo hogar")
public record CreateHouseholdResource(
        @Schema(description = "Alias o nombre del hogar", example = "Hogar Don Alberto")
        String alias,

        @Schema(description = "Número telefónico del hogar", example = "51984123456")
        String phoneNumber,

        @Schema(description = "Correo electrónico asociado", example = "contacto@alivia.pe")
        String contactEmail,

        @Schema(description = "Dirección física (calle y número)", example = "Av. Del Parque Norte 742")
        String address,

        @Schema(description = "Distrito", example = "San Borja")
        String district,

        @Schema(description = "Ciudad", example = "Lima")
        String city,

        @Schema(description = "Dirección completa formateada por Google Maps")
        String formattedAddress,

        @Schema(description = "Latitud geográfica", example = "-12.046374")
        Double latitude,

        @Schema(description = "Longitud geográfica", example = "-77.042793")
        Double longitude,

        @Schema(description = "Identificador único de Google Places", example = "ChIJw8gA1j_JBZERSF7Yf_Q8lJc")
        String placeId,

        @Schema(description = "Identificador del usuario responsable de la cuenta (opcional si se envía Bearer token)", example = "1")
        String responsibleAccountId
) {
}
