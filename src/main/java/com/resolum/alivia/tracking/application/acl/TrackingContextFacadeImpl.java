package com.resolum.alivia.tracking.application.acl;

import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.queries.GetAutonomySummaryQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.AutonomyCounts;
import com.resolum.alivia.tracking.domain.repositories.TrackingStatisticsRepository;
import com.resolum.alivia.tracking.domain.services.AutonomyQueryService;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Implementation of {@link TrackingContextFacade}. */
@Service
public class TrackingContextFacadeImpl implements TrackingContextFacade {

    private static final int MAX_HOME_ALERTS = 200;

    private final AutonomyQueryService autonomy;
    private final TrackingStatisticsRepository statistics;

    public TrackingContextFacadeImpl(AutonomyQueryService autonomy) {
        this(autonomy, null);
    }

    @Autowired
    public TrackingContextFacadeImpl(AutonomyQueryService autonomy, TrackingStatisticsRepository statistics) {
        this.autonomy = autonomy;
        this.statistics = statistics;
    }

    @Override
    public Map<String, Long> countAlertsBySeverity(Instant from, Instant to) {
        requirePeriod(from, to);
        return read(() -> statistics.countAlertsBySeverity(from, to));
    }

    @Override
    public Map<String, Long> countDevicesByConnectionStatus() {
        return read(statistics::countDevicesByConnectionStatus);
    }

    @Override
    public List<HomeAlert> fetchAlertsOfHousehold(Long householdId, Instant from, Instant to) {
        if (householdId == null) throw new IllegalArgumentException("The household is required.");
        requirePeriod(from, to);
        return read(() -> statistics.findAlertsByHousehold(householdId, from, to, MAX_HOME_ALERTS).stream()
                .map(e -> new HomeAlert(e.type().name(), e.severity().name(), e.deviceId(), e.occurredAt()))
                .toList());
    }

    private static void requirePeriod(Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("The period needs a start that is not after its end.");
        }
    }

    /** A failed read is never answered as empty counts. */
    private static <T> T read(Supplier<T> query) {
        try {
            return query.get();
        } catch (DataAccessException failure) {
            throw TrackingStorageUnavailable.forRead(failure);
        }
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
