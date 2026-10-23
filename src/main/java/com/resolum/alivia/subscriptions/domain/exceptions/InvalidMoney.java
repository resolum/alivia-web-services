package com.resolum.alivia.subscriptions.domain.exceptions;

public class InvalidMoney extends IllegalArgumentException {
    public InvalidMoney(String message) {
        super(message);
    }
}
