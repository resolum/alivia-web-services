package com.resolum.alivia.nursing.domain.services;

import com.resolum.alivia.nursing.domain.model.queries.GetMedicationAdministrationQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministration;

/**
 * Read service for medication administrations.
 */
public interface MedicationAdministrationQueryService {

    /**
     * Computes one medication administration with its recorded outcome, if any.
     *
     * @param query the administration query
     * @return the administration
     * @throws com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationNotFoundException
     *         when the administration does not exist
     * @throws com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationAccessDeniedException
     *         when the user is neither the responsible caregiver nor a relative
     */
    MedicationAdministration handle(GetMedicationAdministrationQuery query);
}
