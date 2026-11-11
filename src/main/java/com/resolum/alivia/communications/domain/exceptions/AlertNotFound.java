package com.resolum.alivia.communications.domain.exceptions;

public class AlertNotFound extends RuntimeException {
    public AlertNotFound(Long alertId) {
        super("Alert not found with id: " + alertId);
    }
}
