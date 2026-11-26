package com.resolum.alivia.tracking.application.acl;

import com.resolum.alivia.tracking.domain.model.queries.GetAutonomySummaryQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.AutonomyCounts;
import com.resolum.alivia.tracking.domain.services.AutonomyQueryService;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Implementation of {@link TrackingContextFacade}. */
@Service
public class TrackingContextFacadeImpl implements TrackingContextFacade {

    private final AutonomyQueryService autonomy;

    public TrackingContextFacadeImpl(AutonomyQueryService autonomy) {
        this.autonomy = autonomy;
    }

    @Override
    public AutonomySummary summarizeAutonomy(Long householdId, Long deviceId, Instant from, Instant to) {
        var view = autonomy.handle(new GetAutonomySummaryQuery(householdId, deviceId, from, to));
        return new AutonomySummary(view.householdId(), view.deviceId(), view.from(), view.to(), counts(view.total()),
                view.byDevice().stream().map(d -> new DeviceAutonomy(d.deviceId(), counts(d.counts()))).toList(),
                counts(view.withoutDevice()), view.link().name(), view.lastUpdatedAt(), view.mayHavePendingEvents());
    }

    private static AutonomyCounts counts(com.resolum.alivia.tracking.domain.model.valueobjects.AutonomyCounts counts) {
        return new AutonomyCounts(counts.requested(), counts.succeeded(), counts.failed(), counts.rejectedLowConfidence(),
                counts.rejectedUnauthorized(), counts.noConfirmation(), counts.inProgress(), counts.executedLocally());
    }
}
