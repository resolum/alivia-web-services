package com.resolum.alivia.tracking.domain.services;

import com.resolum.alivia.tracking.domain.model.queries.GetHomeStatusByAssistedPersonQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.HomeStatusView;

/** Answers what the family sees of the devices of a home. */
public interface HomeStatusQueryService {

    /**
     * Every device placed in the home, with its last reliable state, joined with whether the edges of the home are heard.
     *
     * @throws com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible when the person does not exist or
     *         the user may not read it
     * @throws com.resolum.alivia.tracking.domain.exceptions.AssistedPersonWithoutHousehold when the person has no home
     * @throws com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable when the states cannot be read
     */
    HomeStatusView handle(GetHomeStatusByAssistedPersonQuery query);
}
