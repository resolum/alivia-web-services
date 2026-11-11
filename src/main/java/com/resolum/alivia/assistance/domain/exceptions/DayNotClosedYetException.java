package com.resolum.alivia.assistance.domain.exceptions;

public class DayNotClosedYetException extends RuntimeException {
    public DayNotClosedYetException(java.time.LocalDate closingDate, java.time.LocalDate today) {
        super("Closing date " + closingDate + " must be before today " + today);
    }
}
