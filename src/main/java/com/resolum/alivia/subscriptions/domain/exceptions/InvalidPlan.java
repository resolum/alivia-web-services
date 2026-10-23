package com.resolum.alivia.subscriptions.domain.exceptions;

public class InvalidPlan extends IllegalArgumentException {
    public InvalidPlan(String message) {
        super(message);
    }
}
