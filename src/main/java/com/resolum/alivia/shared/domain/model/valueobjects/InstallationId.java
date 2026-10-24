package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record InstallationId(Long installationId) {
    public InstallationId {
        if (installationId == null || installationId <= 0) {
            throw new IllegalArgumentException("Installation ID must be a positive number.");
        }
    }
}
