package com.resolum.alivia.tracking.application.internal.queryservices;

import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.queries.GetAutonomySummaryQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.AutonomySummaryView;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandOutcomeTally;
import com.resolum.alivia.tracking.domain.model.valueobjects.HomeLinkSummary;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import com.resolum.alivia.tracking.domain.services.AutonomyQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link AutonomyQueryService}.
 *
 * <p>The events are read once and counted by command in memory, one command at a time, so a command that was sent twice or
 * whose request and result arrived separately is still one. The state of the link comes from the same reading as the state
 * of the devices, so the metrics and the devices say the same about the same home.</p>
 */
@Slf4j
@Service
public class AutonomyQueryServiceImpl implements AutonomyQueryService {

    private final DeviceEventRepository events;
    private final EdgeStatusRepository edgeStatuses;
    private final ExternalAssetsService assets;
    private final MeterRegistry meters;

    public AutonomyQueryServiceImpl(DeviceEventRepository events, EdgeStatusRepository edgeStatuses,
                                    ExternalAssetsService assets, MeterRegistry meters) {
        this.events = events;
        this.edgeStatuses = edgeStatuses;
        this.assets = assets;
        this.meters = meters;
    }

    @Override
    public AutonomySummaryView handle(GetAutonomySummaryQuery query) {
        var tally = new CommandOutcomeTally();
        // The link of the home is read as in the state of the devices: only the edges the home has now.
        var assigned = assets.fetchAssignedEdgeIds(query.householdId());
        HomeLinkSummary home;
        try {
            events.forEachCommandFact(query.householdId(), query.deviceId(), query.from(), query.to(), tally::add);
            home = HomeLinkSummary.of(edgeStatuses.findAllByHouseholdId(query.householdId()), assigned);
        } catch (RuntimeException failure) {
            meters.counter("tracking.storage.failures", "operation", "read_autonomy").increment();
            log.error("The autonomy of the household {} could not be read.", query.householdId(), failure);
            throw TrackingStorageUnavailable.forRead(failure);
        }
        return new AutonomySummaryView(query.householdId(), query.deviceId(), query.from(), query.to(), tally.total(),
                tally.byDevice(), tally.withoutDevice(), home.link(), home.lastReportAt(), home.mayHavePendingEvents());
    }
}
