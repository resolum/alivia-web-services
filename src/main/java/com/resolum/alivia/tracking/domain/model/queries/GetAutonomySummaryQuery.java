package com.resolum.alivia.tracking.domain.model.queries;

import java.time.Instant;

/**
 * The autonomy of a home in a period, asked by another context that already decided who may see it.
 *
 * @param householdId the home
 * @param deviceId    only the commands of this device; {@code null} for the whole home
 * @param from        the start of the period, included
 * @param to          the end of the period, excluded
 */
public record GetAutonomySummaryQuery(Long householdId, Long deviceId, Instant from, Instant to) {

    public GetAutonomySummaryQuery {
        if (householdId == null) throw new IllegalArgumentException("The household is required.");
        if (from == null || to == null) throw new IllegalArgumentException("The period is required.");
        if (from.isAfter(to)) throw new IllegalArgumentException("The start of the period cannot be after its end.");
    }
}
