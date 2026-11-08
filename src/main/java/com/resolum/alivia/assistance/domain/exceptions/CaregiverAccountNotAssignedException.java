package com.resolum.alivia.assistance.domain.exceptions;

public class CaregiverAccountNotAssignedException extends RuntimeException {
    public CaregiverAccountNotAssignedException() {
        super("Caregiver account has not been assigned to the invitation yet.");
    }
}
