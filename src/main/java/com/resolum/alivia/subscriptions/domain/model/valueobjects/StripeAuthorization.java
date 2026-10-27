package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import java.time.Instant;

public record StripeAuthorization(StripePaymentIntentId intentId, String clientSecret,
                                  PaymentStatus state, Instant captureBefore) { }
