package com.resolum.alivia.profiles.domain.exceptions;

public class InvalidBusinessHoursException extends RuntimeException {
    public InvalidBusinessHoursException() {
        super("Invalid business hours provided");
    }

    public InvalidBusinessHoursException(String message) {
        super(message);
    }
}
