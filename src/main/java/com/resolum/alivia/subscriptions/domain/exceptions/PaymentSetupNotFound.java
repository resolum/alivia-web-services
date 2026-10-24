package com.resolum.alivia.subscriptions.domain.exceptions;

public class PaymentSetupNotFound extends RuntimeException {
    public PaymentSetupNotFound() {
        super("No se encontró el registro de pago para este usuario.");
    }
}
