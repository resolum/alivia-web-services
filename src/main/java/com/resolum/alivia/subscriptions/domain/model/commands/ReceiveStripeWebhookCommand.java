package com.resolum.alivia.subscriptions.domain.model.commands;

public record ReceiveStripeWebhookCommand(String payload, String signature) {
    public ReceiveStripeWebhookCommand {
        if (payload == null || payload.isBlank() || signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("Se requieren el evento y la firma de Stripe.");
        }
    }
}
