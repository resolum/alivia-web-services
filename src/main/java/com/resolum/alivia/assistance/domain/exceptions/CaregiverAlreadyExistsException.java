package com.resolum.alivia.assistance.domain.exceptions;

public class CaregiverAlreadyExistsException extends RuntimeException {
    public CaregiverAlreadyExistsException(Long assistedPersonId, Long userId) {
        super("Active caregiver already exists for assisted person " + assistedPersonId + " and user " + userId);
    }
}
