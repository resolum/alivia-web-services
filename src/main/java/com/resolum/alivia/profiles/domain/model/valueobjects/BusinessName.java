package com.resolum.alivia.profiles.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record BusinessName(String businessName) {
    public BusinessName {
        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException("Business name cannot be null or blank");
        }
    }
}