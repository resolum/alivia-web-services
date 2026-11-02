package com.resolum.alivia.nursing.domain.services;

import com.resolum.alivia.nursing.domain.model.commands.RecordMedicationOutcomeCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministration;

/**
 * Write service for medication administrations.
 */
public interface MedicationAdministrationCommandService {

    /**
     * Records the outcome of a medication administration in the occurrence history.
     *
     * @param command the record command
     * @return the administration with its new outcome
     * @throws IllegalArgumentException when the moment is in the future or before the start
     * @throws com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationNotFoundException
     *         when the administration does not exist
     * @throws com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationAccessDeniedException
     *         when the user is neither the responsible caregiver nor a relative
     * @throws com.resolum.alivia.nursing.domain.exceptions.MedicationOutcomeAlreadyRecordedException
     *         when the administration already has an outcome
     */
    MedicationAdministration handle(RecordMedicationOutcomeCommand command);
}
