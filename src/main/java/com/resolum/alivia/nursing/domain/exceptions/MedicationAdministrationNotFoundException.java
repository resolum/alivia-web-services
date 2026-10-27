package com.resolum.alivia.nursing.domain.exceptions;

import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;

/**
 * Thrown when the assignment does not exist, does not belong to a medication activity, or
 * has no occurrence on the requested date.
 */
public class MedicationAdministrationNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param administrationId the requested administration
     */
    public MedicationAdministrationNotFoundException(MedicationAdministrationId administrationId) {
        super("Medication administration " + administrationId + " was not found.");
    }
}
