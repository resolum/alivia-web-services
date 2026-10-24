package com.resolum.alivia.subscriptions.application.outboundservices;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.VerifiedStripeWebhookEvent;

public interface StripeWebhookVerifier {
    VerifiedStripeWebhookEvent verify(String payload, String signature);
}
