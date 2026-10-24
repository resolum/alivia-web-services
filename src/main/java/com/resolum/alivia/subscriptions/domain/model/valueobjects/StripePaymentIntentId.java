package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record StripePaymentIntentId(String value) {
    public StripePaymentIntentId {
        if (value == null || !value.startsWith("pi_")) throw new IllegalArgumentException("PaymentIntent ID inválido.");
    }
}
