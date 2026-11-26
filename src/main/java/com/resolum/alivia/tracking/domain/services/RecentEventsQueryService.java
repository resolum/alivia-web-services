package com.resolum.alivia.tracking.domain.services;

import com.resolum.alivia.tracking.domain.model.queries.GetRecentEventsByAssistedPersonQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.RecentEventsView;

/** Answers what happened lately in the home of an assisted person. */
public interface RecentEventsQueryService {

    /**
     * The latest events of the home, newest first, whatever device they are about or none (the loss of the connection of
     * an edge, a request for help).
     *
     * @throws com.resolum.alivia.tracking.domain.exceptions.InvalidHistoryQuery {@code invalid_limit} when the limit is
     *         not between 1 and the maximum
     * @throws com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible when the person does not exist or
     *         the user may not read it
     * @throws com.resolum.alivia.tracking.domain.exceptions.AssistedPersonWithoutHousehold when the person has no home
     * @throws com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable when the events cannot be read
     */
    RecentEventsView handle(GetRecentEventsByAssistedPersonQuery query);
}
