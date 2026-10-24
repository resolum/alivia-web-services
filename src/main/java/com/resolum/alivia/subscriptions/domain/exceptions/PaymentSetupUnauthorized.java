package com.resolum.alivia.subscriptions.domain.exceptions;

public class PaymentSetupUnauthorized extends RuntimeException {
    public PaymentSetupUnauthorized() {
        super("Se requiere un usuario autenticado para registrar un medio de pago.");
    }
}
