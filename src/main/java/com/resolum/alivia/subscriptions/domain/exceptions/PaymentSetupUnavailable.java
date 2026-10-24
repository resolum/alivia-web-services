package com.resolum.alivia.subscriptions.domain.exceptions;

public class PaymentSetupUnavailable extends RuntimeException {
    public PaymentSetupUnavailable(String message) {
        super(message);
    }
}
