package com.resolum.alivia.assistance.domain.exceptions;

public class OccurrenceNotScheduledException extends RuntimeException {
    public OccurrenceNotScheduledException(java.time.LocalDate date) {
        super("Occurrence is not scheduled for date " + date);
    }
}
