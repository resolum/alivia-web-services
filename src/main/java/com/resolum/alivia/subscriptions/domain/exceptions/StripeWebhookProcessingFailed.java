package com.resolum.alivia.subscriptions.domain.exceptions;

public class StripeWebhookProcessingFailed extends RuntimeException {
    public StripeWebhookProcessingFailed(Throwable cause) {
        super("No fue posible registrar el evento de Stripe; Stripe puede reintentarlo.", cause);
    }
}
