package com.resolum.alivia.tracking.application.internal.queryservices;

import com.resolum.alivia.tracking.domain.exceptions.InvalidHistoryQuery;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.queries.GetRecentEventsByAssistedPersonQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.HistoryPolicy;
import com.resolum.alivia.tracking.domain.model.valueobjects.RecentEventsView;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.services.RecentEventsQueryService;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link RecentEventsQueryService}.
 *
 * <p>The limits are the ones of the history (D-TR-9): 20 by default and at most 100. The limit is checked before anything is
 * looked up.</p>
 */
@Slf4j
@Service
public class RecentEventsQueryServiceImpl implements RecentEventsQueryService {

    private final TrackingAccessService access;
    private final DeviceEventRepository events;
    private final HistoryPolicy policy;
    private final MeterRegistry meters;

    public RecentEventsQueryServiceImpl(TrackingAccessService access, DeviceEventRepository events, HistoryPolicy policy,
                                        MeterRegistry meters) {
        this.access = access;
        this.events = events;
        this.policy = policy;
        this.meters = meters;
    }

    @Override
    public RecentEventsView handle(GetRecentEventsByAssistedPersonQuery query) {
        int limit = query.limit() == null ? policy.defaultPageSize() : query.limit();
        if (limit < 1 || limit > policy.maxPageSize()) {
            throw new InvalidHistoryQuery("invalid_limit", "The limit must be between 1 and " + policy.maxPageSize() + ".");
        }
        var householdId = access.requireReadableHouseholdOfPerson(query.userId(), query.assistedPersonId());
        try {
            return new RecentEventsView(householdId, limit, events.findRecentByHousehold(householdId, limit));
        } catch (RuntimeException failure) {
            meters.counter("tracking.storage.failures", "operation", "read_recent_events").increment();
            log.error("The recent events of the household {} could not be read.", householdId, failure);
            throw new TrackingStorageUnavailable(failure);
        }
    }
}
