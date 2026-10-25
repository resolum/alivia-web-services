package com.resolum.alivia.profiles.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record LegalName(String name) {
    public LegalName {
        if (name == null || name.isBlank() || name.length() < 2 || name.length() > 500) {
            throw new IllegalArgumentException("Legal name must be between 2 and 500 characters.");
        }
    }
}
