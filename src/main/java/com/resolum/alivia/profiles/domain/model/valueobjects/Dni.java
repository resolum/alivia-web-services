package com.resolum.alivia.profiles.domain.model.valueobjects;

public record Dni(String number) {
    public Dni {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("Number cannot be null or blank");
        }
    }
}
