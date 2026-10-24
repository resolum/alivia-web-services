package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record UserId(Long value) {
    public UserId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Se requiere un User ID válido.");
        }
    }
}
