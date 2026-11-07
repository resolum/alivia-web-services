package com.resolum.alivia.nursing.domain.exceptions;

/**
 * Thrown when a requested medication schedule does not exist or is not a medication activity.
 */
public class MedicationScheduleNotFoundException extends RuntimeException {

    public MedicationScheduleNotFoundException(Long medicationId) {
        super("Medication schedule " + medicationId + " was not found.");
    }
}
