package com.resolum.alivia.subscriptions.application.outboundservices;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetup;

import java.util.UUID;

public interface StripeService {
    String createCheckoutCustomer(String configurationDraftId);

    PaymentSetup createPaymentSetup(String customerId, UUID idempotencyKey);
}
