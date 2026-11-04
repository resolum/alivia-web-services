package com.resolum.alivia.shared.domain.model.valueobjects;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Embeddable;

@Embeddable
@Schema(description = "Dirección física y coordenadas geográficas")
public record Address(
        @Schema(description = "Dirección completa y formateada", example = "Av. El Derby 250, Santiago de Surco, Lima")
        String formattedAddress,

        @Schema(description = "Latitud geográfica en coordenadas decimales (-90 a 90)", example = "-12.109722")
        Double latitude,

        @Schema(description = "Longitud geográfica en coordenadas decimales (-180 a 180)", example = "-76.974444")
        Double longitude,

        @Schema(description = "Identificador único de Google Maps / Google Places", example = "ChIJw8gA1j_JBZERSF7Yf_Q8lJc")
        String placeId) {
    public Address {
        if (formattedAddress == null || formattedAddress.isBlank()) {
            throw new IllegalArgumentException("Formatted address cannot be null or blank.");
        }
        if (latitude == null || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        }
        if (longitude == null || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180.");
        }
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("Place ID cannot be null or blank.");
        }
    }
}
