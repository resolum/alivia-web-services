package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record PaymentId(Long value) {
    public PaymentId {
        if (value == null || value <= 0) throw new IllegalArgumentException("Se requiere un Payment ID válido.");
    }
}
