package com.resolum.alivia.profiles.domain.model.valueobjects;

public record LegalName(String name) {
    public LegalName {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank");
        }
    }
}
