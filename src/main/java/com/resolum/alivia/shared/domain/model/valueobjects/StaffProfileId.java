package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record StaffProfileId(Long staffProfileId) {

    public StaffProfileId {
        if (staffProfileId == null || staffProfileId <= 0) {
            throw new IllegalArgumentException("Staff profile ID must be a positive number.");
        }
    }
}
