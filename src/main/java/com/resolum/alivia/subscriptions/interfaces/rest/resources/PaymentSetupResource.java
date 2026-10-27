package com.resolum.alivia.subscriptions.interfaces.rest.resources;

public record PaymentSetupResource(String setupIntentId, String clientSecret, String publishableKey) {
}
