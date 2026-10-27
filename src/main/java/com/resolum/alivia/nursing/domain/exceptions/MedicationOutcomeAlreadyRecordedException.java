package com.resolum.alivia.nursing.domain.exceptions;

import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;

/**
 * Thrown when the administration already has a recorded outcome. The history is immutable,
 * so the existing outcome is never overwritten.
 */
public class MedicationOutcomeAlreadyRecordedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param administrationId the administration
     */
    public MedicationOutcomeAlreadyRecordedException(MedicationAdministrationId administrationId) {
        super("Medication administration " + administrationId + " already has a recorded outcome.");
    }
}
