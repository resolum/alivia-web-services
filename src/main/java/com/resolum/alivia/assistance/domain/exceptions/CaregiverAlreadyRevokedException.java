package com.resolum.alivia.assistance.domain.exceptions;

public class CaregiverAlreadyRevokedException extends RuntimeException {
    public CaregiverAlreadyRevokedException(Long id) {
        super("Caregiver with ID " + id + " is already revoked.");
    }
}
