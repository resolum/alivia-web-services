package com.resolum.alivia.assistance.domain.exceptions;

public class ActivityAssignmentNotActiveException extends RuntimeException {
    public ActivityAssignmentNotActiveException(Long id) {
        super("Activity assignment with ID " + id + " is not active.");
    }
}
