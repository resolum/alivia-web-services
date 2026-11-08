package com.resolum.alivia.communications.domain.exceptions;

import com.resolum.alivia.communications.domain.model.valueobjects.AlertStatus;

public class InvalidAlertStatusTransition extends RuntimeException {
    public InvalidAlertStatusTransition(AlertStatus from, AlertStatus to) {
        super("Invalid alert status transition from " + from + " to " + to);
    }
}
