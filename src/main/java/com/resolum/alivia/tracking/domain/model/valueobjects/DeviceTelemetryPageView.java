package com.resolum.alivia.tracking.domain.model.valueobjects;

import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;

import java.time.Instant;
import java.util.List;

/**
 * A page of the diagnostics readings of a device.
 *
 * @param deviceId the device
 * @param from     the start of the period, included
 * @param to       the end of the period, excluded
 * @param page     the number of the page, from 0
 * @param size     the size of the page that was asked
 * @param total    how many readings there are in the period
 * @param items    the readings of the page, newest first
 */
public record DeviceTelemetryPageView(Long deviceId, Instant from, Instant to, int page, int size, long total,
                                      List<TelemetrySample> items) {

    public DeviceTelemetryPageView {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public int totalPages() {
        return (int) ((total + size - 1) / size);
    }
}
