package com.resolum.alivia.nursing.domain.exceptions;

import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;

/**
 * Thrown when the authenticated user is neither the active caregiver responsible for the
 * assignment nor an active relative of the assisted person.
 */
public class MedicationAdministrationAccessDeniedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param administrationId the requested administration
     */
    public MedicationAdministrationAccessDeniedException(MedicationAdministrationId administrationId) {
        super("You cannot access medication administration " + administrationId + ".");
    }
}
