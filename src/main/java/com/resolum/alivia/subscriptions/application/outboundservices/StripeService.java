package com.resolum.alivia.subscriptions.application.outboundservices;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetup;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetupStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.UserId;

import java.util.UUID;

public interface StripeService {
    String createCustomer(UserId userId);

    PaymentSetup createPaymentSetup(String customerId, UUID idempotencyKey);

    PaymentSetupStatus retrievePaymentSetup(String setupIntentId);
}
