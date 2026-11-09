package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import java.math.BigDecimal;

public record SubscriptionResource(
        String id,
        String status,
        String planCode,
        String planName,
        String currency,
        BigDecimal monthlyAmount,
        String startedOn,
        String renewsOn,
        Integer rooms,
        Integer devices,
        PaymentMethodResource paymentMethod,
        SubscriptionPaymentResource lastPayment,
        String pendingPlanCode
) {
    public record PaymentMethodResource(
            String brand,
            String last4,
            String holderName,
            boolean autoCharge
    ) {}

    public record SubscriptionPaymentResource(
            String id,
            BigDecimal amount,
            String paidAt,
            String status,
            String receiptUrl
    ) {}
}
