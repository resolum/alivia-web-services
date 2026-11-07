package com.resolum.alivia.nursing.domain.services;

import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationByIdQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationsByAssistedPersonQuery;

import java.util.List;

/**
 * Domain query service for medication schedules.
 */
public interface MedicationScheduleQueryService {

    /**
     * Lists the medication schedules of an assisted person visible to the user.
     *
     * @param query the query
     * @return the list of care activities representing medication schedules
     */
    List<CareActivity> handle(GetMedicationsByAssistedPersonQuery query);

    /**
     * Retrieves a medication schedule by its identifier.
     *
     * @param query the query
     * @return the care activity representing the medication schedule
     */
    CareActivity handle(GetMedicationByIdQuery query);
}
