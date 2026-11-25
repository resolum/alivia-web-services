package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Instant;
import java.util.List;

/**
 * The devices of a home with their last known state, and whether the home is being heard.
 *
 * @param householdId          the home
 * @param link                 whether its edges are heard
 * @param offlineSince         since when the first edge that is not heard stopped reporting; {@code null} when none
 * @param lastUpdatedAt        the latest thing the backend knows about the home (a report of an edge or a state kept);
 *                             {@code null} when nothing has ever been reported
 * @param mayHavePendingEvents whether events may be waiting in an edge: one is offline, or said it has some to send
 * @param devices              the devices placed in the home, by id
 */
public record HomeStatusView(Long householdId, HomeLink link, Instant offlineSince, Instant lastUpdatedAt,
                             boolean mayHavePendingEvents, List<DeviceStatusView> devices) {

    public HomeStatusView {
        devices = devices == null ? List.of() : List.copyOf(devices);
    }
}
