package com.resolum.alivia.subscriptions.domain.model.queries;

public record GetPaymentSetupStatusQuery(String authenticatedUserId, String setupIntentId) {
    public GetPaymentSetupStatusQuery {
        if (setupIntentId == null || setupIntentId.isBlank()) {
            throw new IllegalArgumentException("Se requiere el ID del SetupIntent.");
        }
    }
}
