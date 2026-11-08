package com.resolum.alivia.assistance.domain.exceptions;

public class CaregiverNotFoundException extends RuntimeException {
    public CaregiverNotFoundException(Long id) {
        super("Caregiver with ID " + id + " was not found.");
    }
}
