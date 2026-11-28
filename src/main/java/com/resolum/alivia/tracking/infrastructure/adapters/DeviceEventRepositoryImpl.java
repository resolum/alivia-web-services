package com.resolum.alivia.tracking.infrastructure.adapters;

import com.resolum.alivia.tracking.domain.model.events.DeviceEventRecordedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandFact;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventInsertion;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceEventDocument;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** {@link DeviceEventRepository} over the {@code device_events} collection. */
@Component
public class DeviceEventRepositoryImpl implements DeviceEventRepository {

    private static final int MAX_PAGE_SIZE = 200;

    private final MongoTemplate mongo;
    private final ApplicationEventPublisher publisher;

    public DeviceEventRepositoryImpl(MongoTemplate mongo) {
        this(mongo, event -> { });
    }

    @Autowired
    public DeviceEventRepositoryImpl(MongoTemplate mongo, ApplicationEventPublisher publisher) {
        this.mongo = mongo;
        this.publisher = publisher;
    }

    /** Tells the ones who follow the home live; a failure there never undoes what was kept. */
    private void published(DeviceEvent event) {
        try {
            publisher.publishEvent(new DeviceEventRecordedEvent(event));
        } catch (RuntimeException ignored) {
            // the event is kept; the live view catches up with the next read of the history
        }
    }

    @Override
    public EventInsertion insert(DeviceEvent event) {
        try {
            mongo.insert(DeviceEventDocument.from(event));
            published(event);
            return EventInsertion.INSERTED;
        } catch (DuplicateKeyException duplicate) {
            // The only unique index is (edge, event id): this edge already reported this event.
            return EventInsertion.DUPLICATE;
        }
    }

    /** Duplicate key, as MongoDB numbers it. */
    private static final int DUPLICATE_KEY = 11000;

    @Override
    public List<EventInsertion> insertAll(List<DeviceEvent> batch) {
        if (batch.isEmpty()) return List.of();
        var result = new ArrayList<>(Collections.nCopies(batch.size(), EventInsertion.INSERTED));
        var documents = batch.stream().map(DeviceEventDocument::from).toList();
        try {
            // Unordered: an event already kept does not stop the ones after it.
            mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, DeviceEventDocument.class).insert(documents).execute();
        } catch (BulkOperationException failure) {
            for (var error : failure.getErrors()) {
                if (error.getCode() != DUPLICATE_KEY) throw failure;
                result.set(error.getIndex(), EventInsertion.DUPLICATE);
            }
        }
        for (int i = 0; i < batch.size(); i++) {
            if (result.get(i) == EventInsertion.INSERTED) published(batch.get(i));
        }
        return result;
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
                .with(Sort.by(Sort.Direction.DESC, "occurredAt", "id")).limit(limit);
        return mongo.find(query, DeviceEventDocument.class).stream().map(DeviceEventDocument::toDomain).toList();
    }
}
