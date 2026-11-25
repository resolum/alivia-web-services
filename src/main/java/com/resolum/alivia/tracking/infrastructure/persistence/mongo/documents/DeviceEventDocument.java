package com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandAction;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandFact;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOrigin;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventResult;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventSeverity;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * How an event is kept. The indexes are the ones the queries of Tracking need: the unique pair (edge, event id) that
 * makes the ingestion idempotent, the history of a device and the recent events of a household.
 */
@Document("device_events")
@CompoundIndexes({
        @CompoundIndex(name = "edge_event_unique", def = "{'edgeId': 1, 'eventId': 1}", unique = true),
        // The id closes the order: two events of one instant keep their place from one page to the next. The index of
        // an earlier version (household_device_time, without the id) is not used any more and can be dropped.
        @CompoundIndex(name = "household_device_time_id",
                def = "{'householdId': 1, 'deviceId': 1, 'occurredAt': -1, '_id': -1}"),
        @CompoundIndex(name = "household_time", def = "{'householdId': 1, 'occurredAt': -1}")
})
public class DeviceEventDocument {

    @Id
    private String id;
    private String eventId;
    private Long edgeId;
    private Long installationId;
    private Long householdId;
    private Long deviceId;
    private String type;
    private String severity;
    private String result;
    private String origin;
    private Instant occurredAt;
    private Instant receivedAt;
    /** Only the events of a command carry it: a partial index keeps the lookup by command small. */
    @Indexed(name = "command", partialFilter = "{'commandId': {'$exists': true}}")
    private String commandId;
    private String action;
    private Double confidence;
    private Integer batteryPct;
    private String detail;
    private Map<String, Object> attributes;

    protected DeviceEventDocument() {
    }

    public static DeviceEventDocument from(DeviceEvent event) {
        var document = new DeviceEventDocument();
        document.eventId = event.eventId();
        document.edgeId = event.edgeId();
        document.installationId = event.installationId();
        document.householdId = event.householdId();
        document.deviceId = event.deviceId();
        document.type = event.type().name();
        document.severity = event.severity().name();
        document.result = event.result() == null ? null : event.result().name();
        document.origin = event.origin().name();
        document.occurredAt = event.occurredAt();
        document.receivedAt = event.receivedAt();
        document.commandId = event.commandId();
        document.action = event.action() == null ? null : event.action().name();
        document.confidence = event.confidence();
        document.batteryPct = event.batteryPct();
        document.detail = event.detail();
        document.attributes = event.attributes().isEmpty() ? null : event.attributes().values();
        return document;
    }

    /** What the autonomy metrics read of the event; the document may have been read with only these fields. */
    public CommandFact toCommandFact() {
        return new CommandFact(commandId, EventType.valueOf(type), result == null ? null : EventResult.valueOf(result),
                EventOrigin.valueOf(origin), occurredAt, deviceId);
    }

    public DeviceEvent toDomain() {
        return new DeviceEvent(eventId, edgeId, installationId, householdId, deviceId, EventType.valueOf(type),
                EventSeverity.valueOf(severity), result == null ? null : EventResult.valueOf(result),
                EventOrigin.valueOf(origin), occurredAt, receivedAt, commandId,
                action == null ? null : CommandAction.valueOf(action), confidence, batteryPct, detail,
                new EventAttributes(attributes));
    }
}
