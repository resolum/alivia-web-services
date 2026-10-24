package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import java.time.Instant;

public record PreauthorizationResult(SubscriptionId subscriptionId, PaymentId paymentId,
                                     PaymentStatus status, String stripePaymentIntentId,
                                     String clientSecret, Instant captureBefore) { }
