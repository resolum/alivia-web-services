package com.resolum.alivia.assistance.domain.exceptions;

public class OccurrenceEndedException extends RuntimeException {
    public OccurrenceEndedException(java.time.LocalDateTime now, java.time.LocalDateTime end) {
        super("Occurrence has already ended. Current: " + now + ", end: " + end);
    }
}
