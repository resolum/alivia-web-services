package com.resolum.alivia.communications.domain.exceptions;

import com.resolum.alivia.communications.domain.model.valueobjects.EmailStatus;

public class InvalidEmailStatusTransition extends RuntimeException {
    public InvalidEmailStatusTransition(EmailStatus from, EmailStatus to) {
        super("Invalid email status transition from " + from + " to " + to);
    }
}
