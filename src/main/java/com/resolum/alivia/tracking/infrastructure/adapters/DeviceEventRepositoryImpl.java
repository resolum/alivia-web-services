package com.resolum.alivia.tracking.infrastructure.adapters;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandFact;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventInsertion;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceEventDocument;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** {@link DeviceEventRepository} over the {@code device_events} collection. */
@Component
public class DeviceEventRepositoryImpl implements DeviceEventRepository {

    private static final int MAX_PAGE_SIZE = 200;

    private final MongoTemplate mongo;

    public DeviceEventRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public EventInsertion insert(DeviceEvent event) {
        try {
            mongo.insert(DeviceEventDocument.from(event));
            return EventInsertion.INSERTED;
        } catch (DuplicateKeyException duplicate) {
            // The only unique index is (edge, event id): this edge already reported this event.
            return EventInsertion.DUPLICATE;
        }
    }

    @Override
    public Optional<DeviceEvent> findByEdgeAndEventId(Long edgeId, String eventId) {
        var query = new Query(where("edgeId").is(edgeId).and("eventId").is(eventId));
        return Optional.ofNullable(mongo.findOne(query, DeviceEventDocument.class)).map(DeviceEventDocument::toDomain);
    }

    @Override
    public EventPage findByDevice(Long householdId, Long deviceId, Instant from, Instant to, EventType type, int page,
                                  int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("The page must be 0 or more and the size between 1 and " + MAX_PAGE_SIZE + ".");
        }
        var criteria = where("householdId").is(householdId).and("deviceId").is(deviceId)
                .and("occurredAt").gte(from).lt(to);
        if (type != null) criteria = criteria.and("type").is(type.name());
        var query = new Query(criteria);
        long total = mongo.count(query, DeviceEventDocument.class);
        if (total == 0 || (long) page * size >= total) return new EventPage(List.of(), total);
        // The id breaks the tie of two events of one instant, so no page repeats or skips one.
        var items = mongo.find(query.with(Sort.by(Sort.Direction.DESC, "occurredAt", "id")).skip((long) page * size).limit(size),
                DeviceEventDocument.class).stream().map(DeviceEventDocument::toDomain).toList();
        return new EventPage(items, total);
    }

    @Override
    public void forEachCommandFact(Long householdId, Long deviceId, Instant from, Instant to, Consumer<CommandFact> action) {
        var criteria = where("householdId").is(householdId).and("occurredAt").gte(from).lt(to)
                .and("type").in(EventType.COMMAND_REQUESTED.name(), EventType.COMMAND_RESULT.name(),
                        EventType.COMMAND_REJECTED.name())
                .and("commandId").exists(true);
        if (deviceId != null) criteria = criteria.and("deviceId").is(deviceId);
        // Only what the metrics need is read: no detail, no attributes, nothing of what was said.
        var query = new Query(criteria);
        query.fields().include("commandId", "type", "result", "origin", "occurredAt", "deviceId");
        try (var events = mongo.stream(query, DeviceEventDocument.class)) {
            events.forEach(document -> action.accept(document.toCommandFact()));
        }
    }

    @Override
    public List<DeviceEvent> findRecentByHousehold(Long householdId, int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("The limit must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        var query = new Query(where("householdId").is(householdId))
                .with(Sort.by(Sort.Direction.DESC, "occurredAt")).limit(limit);
        return mongo.find(query, DeviceEventDocument.class).stream().map(DeviceEventDocument::toDomain).toList();
    }
}
