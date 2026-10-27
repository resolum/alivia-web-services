package com.resolum.alivia.subscriptions.domain.exceptions;

public class InvalidActuatorSelection extends IllegalArgumentException {
    public InvalidActuatorSelection(String message) {
        super(message);
    }
}
