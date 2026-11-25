package com.resolum.alivia.tracking.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The devices of the home of an assisted person with their last known state.
 *
 * @param edgeLink             {@code ONLINE}, {@code OFFLINE} (at least one edge is not heard) or {@code UNKNOWN} (none
 *                             has ever reported)
 * @param offlineSince         since when the first silent edge stopped reporting; absent while online
 * @param lastUpdatedAt        the latest thing known about the home; absent when nothing was ever reported
 * @param mayHavePendingEvents whether events may be waiting in an edge that has not synchronized them
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeStatusResource(Long personId, Long householdId, String edgeLink, Instant offlineSince,
                                 Instant lastUpdatedAt, boolean mayHavePendingEvents,
                                 List<HomeDeviceStatusResource> devices) {
}
