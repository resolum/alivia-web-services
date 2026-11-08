package com.resolum.alivia.assistance.domain.exceptions;

public class ActivityAssignmentNotFoundException extends RuntimeException {
    public ActivityAssignmentNotFoundException(Long id) {
        super("Activity assignment with ID " + id + " was not found.");
    }
}
