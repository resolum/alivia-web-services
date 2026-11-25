package com.resolum.alivia.tracking.domain.repositories;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventInsertion;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;

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

    /**
     * A page of the events of a device of a household in the period ({@code from} included, {@code to} excluded), newest
     * first, with how many there are in all.
     *
     * @param type only the events of this type; {@code null} for every type
     * @param page the number of the page, from 0
     */
    EventPage findByDevice(Long householdId, Long deviceId, Instant from, Instant to, EventType type, int page, int size);

    /** The latest events of a household, newest first. */
    List<DeviceEvent> findRecentByHousehold(Long householdId, int limit);
}
