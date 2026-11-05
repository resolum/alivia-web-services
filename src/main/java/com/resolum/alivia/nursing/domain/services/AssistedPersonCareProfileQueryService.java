package com.resolum.alivia.nursing.domain.services;

import com.resolum.alivia.nursing.domain.model.aggregates.AssistedPersonCareProfile;
import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonCareProfileQuery;

import java.util.Optional;

/**
 * Query service for assisted person care profiles.
 */
public interface AssistedPersonCareProfileQueryService {

    /**
     * Returns the care profile registered by the requester.
     *
     * @param query the query
     * @return the profile, if any
     */
    Optional<AssistedPersonCareProfile> handle(GetAssistedPersonCareProfileQuery query);
}
