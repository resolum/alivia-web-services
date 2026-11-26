package com.resolum.alivia.tracking.domain.repositories;

import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;
import com.resolum.alivia.tracking.domain.model.valueobjects.TelemetryPage;

import java.time.Instant;
import java.util.List;

/** Keeps the diagnostics readings of the devices. */
public interface TelemetrySampleRepository {

    void save(TelemetrySample sample);

    /**
     * A page of the readings of a device of a household in the period ({@code from} included, {@code to} excluded),
     * newest first, with how many there are in all.
     *
     * @param page the number of the page, from 0
     */
    TelemetryPage findByDevice(Long householdId, Long deviceId, Instant from, Instant to, int page, int size);
}
