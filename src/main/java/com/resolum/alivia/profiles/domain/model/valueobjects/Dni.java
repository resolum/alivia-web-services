package com.resolum.alivia.profiles.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record Dni(String dni) {
    public Dni {
        if (dni == null || dni.isBlank() || !dni.matches("\\d{8}")) {
            throw new IllegalArgumentException("Dni must be 8 digits.");
        }
    }
}
