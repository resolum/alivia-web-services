package com.resolum.alivia.tracking.domain.repositories;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandFact;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventInsertion;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;

import java.time.Instant;
import java.util.function.Consumer;
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

    /**
     * Passes, one at a time and in no particular order, what the metrics need of every event about a command of the
     * household in the period ({@code from} included, {@code to} excluded): requests, results and rejections. The events
     * are not held in memory, so a long period costs time but not space.
     *
     * @param deviceId only the events of this device; {@code null} for every event
     */
    void forEachCommandFact(Long householdId, Long deviceId, Instant from, Instant to, Consumer<CommandFact> action);

    /** The latest events of a household, newest first. */
    List<DeviceEvent> findRecentByHousehold(Long householdId, int limit);
}
