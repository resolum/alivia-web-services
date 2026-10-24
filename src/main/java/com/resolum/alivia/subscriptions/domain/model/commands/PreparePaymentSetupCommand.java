package com.resolum.alivia.subscriptions.domain.model.commands;

import java.util.UUID;

public record PreparePaymentSetupCommand(String authenticatedUserId, UUID idempotencyKey) {
    public PreparePaymentSetupCommand {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("Se requiere Idempotency-Key para poder reintentar sin duplicar la operación.");
        }
    }
}
