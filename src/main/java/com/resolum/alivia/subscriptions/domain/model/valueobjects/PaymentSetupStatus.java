package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record PaymentSetupStatus(String setupIntentId, String customerId, String status, String paymentMethodId) {
}
