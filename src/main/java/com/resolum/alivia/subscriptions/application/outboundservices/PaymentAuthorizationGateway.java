package com.resolum.alivia.subscriptions.application.outboundservices;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripeAuthorization;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import java.util.UUID;

public interface PaymentAuthorizationGateway {
    StripeAuthorization create(String customerId, String paymentMethodId, long amountInCents, String currency, UUID idempotencyKey);
    StripeAuthorization retrieve(StripePaymentIntentId id);
    void cancel(StripePaymentIntentId id);
}
