package com.resolum.alivia.subscriptions.interfaces.rest.resources;

public record PaymentSetupStatusResource(String setupIntentId, String status, boolean paymentMethodRegistered) {
}
