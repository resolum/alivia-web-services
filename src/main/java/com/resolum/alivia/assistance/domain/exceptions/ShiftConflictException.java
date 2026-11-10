package com.resolum.alivia.assistance.domain.exceptions;

public class ShiftConflictException extends RuntimeException {
    public ShiftConflictException(String message) {
        super(message);
    }

    public ShiftConflictException() {
        super("Shift coincides with another scheduled shift.");
    }
}
