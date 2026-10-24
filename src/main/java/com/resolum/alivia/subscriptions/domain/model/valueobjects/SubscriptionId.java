package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record SubscriptionId(Long value) {
    public SubscriptionId {
        if (value == null || value <= 0) throw new IllegalArgumentException("Se requiere un Subscription ID válido.");
    }
}
