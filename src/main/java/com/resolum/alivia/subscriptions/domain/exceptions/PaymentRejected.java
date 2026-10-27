package com.resolum.alivia.subscriptions.domain.exceptions;

public class PaymentRejected extends RuntimeException {
    public PaymentRejected() { super("La tarjeta rechazó la preautorización."); }
}
