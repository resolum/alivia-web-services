package com.resolum.alivia.tracking.domain.model.valueobjects;

import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;

import java.util.List;

/**
 * One page of readings and how many there are in all.
 *
 * @param items the readings of the page, newest first
 * @param total how many readings match
 */
public record TelemetryPage(List<TelemetrySample> items, long total) {

    public TelemetryPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
