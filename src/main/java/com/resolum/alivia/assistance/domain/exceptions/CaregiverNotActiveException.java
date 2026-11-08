package com.resolum.alivia.assistance.domain.exceptions;

public class CaregiverNotActiveException extends RuntimeException {
    public CaregiverNotActiveException(Long id) {
        super("Caregiver with ID " + id + " is not active.");
    }
}
