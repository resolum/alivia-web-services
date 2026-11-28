package com.resolum.alivia.tracking.domain.repositories;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregates over the events and states, for the contexts that summarize them (Analytics, the care summary of
 * Assistance). An alert is an event whose severity is not {@code INFORMATIVE}.
 */
public interface TrackingStatisticsRepository {

    /** Alerts of every household in the period ({@code from} included, {@code to} excluded), by severity name. */
    Map<String, Long> countAlertsBySeverity(Instant from, Instant to);

    /** The last known state of every device that ever reported, counted by connection status name. */
    Map<String, Long> countDevicesByConnectionStatus();

    /** The alerts of a household in the period, oldest first, at most {@code limit}. */
    List<DeviceEvent> findAlertsByHousehold(Long householdId, Instant from, Instant to, int limit);
}
