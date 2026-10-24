package com.resolum.alivia.subscriptions.domain.exceptions;

public class StripeWebhookUnavailable extends RuntimeException {
    public StripeWebhookUnavailable() {
        super("El secreto de firma del webhook de Stripe no está configurado.");
    }
}
