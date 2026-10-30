package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record BusinessProfileId(Long businessProfileId) {

    public BusinessProfileId {
        if (businessProfileId == null || businessProfileId <= 0) {
            throw new IllegalArgumentException("Business profile ID must be a positive number.");
        }
    }
}
