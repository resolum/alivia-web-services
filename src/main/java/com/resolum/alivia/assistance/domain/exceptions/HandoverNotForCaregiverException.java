package com.resolum.alivia.assistance.domain.exceptions;

public class HandoverNotForCaregiverException extends RuntimeException {
    public HandoverNotForCaregiverException(Long handoverId, Long caregiverId) {
        super("Caregiver " + caregiverId + " is not authorized to act on handover " + handoverId + ".");
    }
}
