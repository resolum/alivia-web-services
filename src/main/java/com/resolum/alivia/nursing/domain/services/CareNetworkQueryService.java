package com.resolum.alivia.nursing.domain.services;

import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonsQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetCaregiversQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.AssistedPersonSummary;
import com.resolum.alivia.nursing.domain.model.valueobjects.CaregiverSummary;

import java.util.List;

/**
 * Read service for the care network of a household.
 *
 * <p>An assisted person belongs to a household when its user is an active member of the
 * household and an active care membership points to its person profile.</p>
 */
public interface CareNetworkQueryService {

    /**
     * Lists the assisted persons of the household.
     *
     * @param query the query
     * @return the assisted persons ordered by profile identifier
     * @throws com.resolum.alivia.nursing.domain.exceptions.CareNetworkHouseholdNotFoundException
     *         when the household does not exist
     * @throws com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException
     *         when the user cannot read the household
     */
    List<AssistedPersonSummary> handle(GetAssistedPersonsQuery query);

    /**
     * Lists the caregivers linked to the assisted persons of the household.
     *
     * <p>Any member who can read the household gets the caregivers; their email is filled in only
     * when the user may also manage the household.</p>
     *
     * @param query the query
     * @return the caregivers ordered by assisted person and caregiver profile identifier
     * @throws com.resolum.alivia.nursing.domain.exceptions.CareNetworkHouseholdNotFoundException
     *         when the household does not exist
     * @throws com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException
     *         when the user cannot read the household
     */
    List<CaregiverSummary> handle(GetCaregiversQuery query);
}
