package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record VerifiedStripeWebhookEvent(String id, String type, String objectId) {
    public VerifiedStripeWebhookEvent {
        if (id == null || id.isBlank() || type == null || type.isBlank()) {
            throw new IllegalArgumentException("El evento de Stripe no tiene identificador o tipo.");
        }
    }


    public VerifiedStripeWebhookEvent(String id, String type) {
        this(id, type, null);
    }
}
