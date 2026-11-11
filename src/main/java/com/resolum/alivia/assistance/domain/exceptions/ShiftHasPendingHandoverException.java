package com.resolum.alivia.assistance.domain.exceptions;

public class ShiftHasPendingHandoverException extends RuntimeException {
    public ShiftHasPendingHandoverException(Long shiftId) {
        super("Shift " + shiftId + " has an open handover pending resolution.");
    }
}
