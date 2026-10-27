package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record Address(String formattedAddress, Double latitude, Double longitude, String placeId) {
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
