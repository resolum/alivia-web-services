package com.resolum.alivia.tracking.domain.model.valueobjects;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;

import java.util.List;

/**
 * One page of events and how many there are in all.
 *
 * @param items the events of the page, newest first
 * @param total how many events match, in every page
 */
public record EventPage(List<DeviceEvent> items, long total) {

    public EventPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
