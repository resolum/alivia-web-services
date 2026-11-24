package com.resolum.alivia.tracking.domain.repositories;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventInsertion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Keeps the events of the homes. An event is never changed once kept. */
public interface DeviceEventRepository {

    /**
     * Keeps the event unless the edge already reported it. The identity is the pair (edge, event id), and the check is
     * atomic: several reports of the same event at once keep one and answer {@code DUPLICATE} to the others.
     */
    EventInsertion insert(DeviceEvent event);

    Optional<DeviceEvent> findByEdgeAndEventId(Long edgeId, String eventId);

    /** The events of a device of a household in the period ({@code from} included, {@code to} excluded), newest first. */
    List<DeviceEvent> findByDevice(Long householdId, Long deviceId, Instant from, Instant to, int page, int size);

    /** The latest events of a household, newest first. */
    List<DeviceEvent> findRecentByHousehold(Long householdId, int limit);
}
