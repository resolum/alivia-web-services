package com.resolum.alivia.assistance.domain.exceptions;

public class OccurrenceNotStartedException extends RuntimeException {
    public OccurrenceNotStartedException(java.time.LocalDateTime now, java.time.LocalDateTime start) {
        super("Occurrence has not started yet. Current: " + now + ", start: " + start);
    }
}
