package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record PaymentSetup(String setupIntentId, String clientSecret, String publishableKey) {
    public PaymentSetup {
        if (setupIntentId == null || setupIntentId.isBlank() || clientSecret == null || clientSecret.isBlank()
                || publishableKey == null || publishableKey.isBlank()) {
            throw new IllegalArgumentException("La preparación del medio de pago está incompleta.");
        }
    }
}
