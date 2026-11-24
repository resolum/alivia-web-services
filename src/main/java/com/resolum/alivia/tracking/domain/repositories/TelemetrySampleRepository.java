package com.resolum.alivia.tracking.domain.repositories;

import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;

import java.time.Instant;
import java.util.List;

/** Keeps the diagnostics readings of the devices. */
public interface TelemetrySampleRepository {

    void save(TelemetrySample sample);

    /** The readings of a device of a household in the period ({@code from} included, {@code to} excluded), newest first. */
    List<TelemetrySample> findByDevice(Long householdId, Long deviceId, Instant from, Instant to, int limit);
}
