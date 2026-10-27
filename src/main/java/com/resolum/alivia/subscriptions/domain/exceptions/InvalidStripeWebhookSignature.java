package com.resolum.alivia.subscriptions.domain.exceptions;

public class InvalidStripeWebhookSignature extends RuntimeException {
    public InvalidStripeWebhookSignature() {
        super("La firma del evento de Stripe no es válida.");
    }
}
