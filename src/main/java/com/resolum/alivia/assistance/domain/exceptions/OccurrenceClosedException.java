package com.resolum.alivia.assistance.domain.exceptions;

public class OccurrenceClosedException extends RuntimeException {
    public OccurrenceClosedException(Long assignmentId, java.time.LocalDate date) {
        super("Occurrence for assignment " + assignmentId + " on " + date + " is already closed.");
    }
}
