package com.resolum.alivia.analytics.domain.model.valueobjects;

import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade.AutonomyCounts;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade.DeviceAutonomy;

import java.time.Instant;
import java.util.List;

/**
 * The voice commands of the home of an assisted person in a period, each command counted once whatever was resent.
 * Counts only: no audio, transcription or text.
 *
 * @param lastUpdatedAt        the latest report of any edge of the home; {@code null} when none ever reported
 * @param mayHavePendingEvents events may still be waiting in an edge, so the counts may grow
 */
public record AutonomyMetrics(Long personId, Instant from, Instant to, Instant computedAt, AutonomyCounts total,
                              List<DeviceAutonomy> byDevice, AutonomyCounts withoutDevice, String edgeLink,
                              Instant lastUpdatedAt, boolean mayHavePendingEvents) {

    private static final AutonomyCounts ZERO = new AutonomyCounts(0, 0, 0, 0, 0, 0, 0, 0);

    /** A person who has no home yet has no devices, so no commands. */
    public static AutonomyMetrics withoutHome(Long personId, AnalyticsPeriod period, Instant computedAt) {
        return new AutonomyMetrics(personId, period.from(), period.to(), computedAt, ZERO, List.of(), ZERO, "UNKNOWN",
                null, false);
    }
}
