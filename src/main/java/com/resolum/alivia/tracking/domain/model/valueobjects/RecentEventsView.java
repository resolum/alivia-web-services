package com.resolum.alivia.tracking.domain.model.valueobjects;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;

import java.util.List;

/**
 * The latest things that happened in the home of an assisted person, newest first: what the people asked their devices to
 * do, what the devices did, the alerts and the losses of connection of the edges.
 *
 * @param householdId the home
 * @param limit       how many events were asked for
 * @param items       the events, newest first; fewer than {@code limit} when the home has fewer
 */
public record RecentEventsView(Long householdId, int limit, List<DeviceEvent> items) {

    public RecentEventsView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
