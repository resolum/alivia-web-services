package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record PlanId(Long value) {
    public PlanId {
        if (value == null || value <= 0) throw new IllegalArgumentException("Se requiere un Plan ID válido.");
    }
}
