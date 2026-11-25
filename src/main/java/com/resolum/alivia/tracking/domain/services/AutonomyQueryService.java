package com.resolum.alivia.tracking.domain.services;

import com.resolum.alivia.tracking.domain.model.queries.GetAutonomySummaryQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.AutonomySummaryView;

/** Counts what the people of a home asked their devices to do and how it ended. */
public interface AutonomyQueryService {

    /**
     * The commands of the period counted once each, whatever the number of events about them or the number of times an
     * event was sent. It does not decide who may see the home: the caller does.
     *
     * @throws com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable when the events cannot be read; an
     *         empty summary is never answered for a failed read
     */
    AutonomySummaryView handle(GetAutonomySummaryQuery query);
}
