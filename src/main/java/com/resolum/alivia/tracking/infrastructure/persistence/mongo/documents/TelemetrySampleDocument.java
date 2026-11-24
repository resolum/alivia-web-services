package com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents;

import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * A diagnostics reading. {@code expiresAt} carries the time-to-live index: MongoDB deletes the document when that moment
 * comes and ignores the ones without it, so nothing is deleted until a retention is configured.
 */
@Document("telemetry_samples")
@CompoundIndex(name = "household_device_time", def = "{'householdId': 1, 'deviceId': 1, 'sampledAt': -1}")
public class TelemetrySampleDocument {

    @Id
    private String id;
    private Long deviceId;
    private Long edgeId;
    private Long householdId;
    private Instant sampledAt;
    private Map<String, Object> metrics;
    @Indexed(name = "expires", expireAfterSeconds = 0)
    private Instant expiresAt;

    protected TelemetrySampleDocument() {
    }

    public static TelemetrySampleDocument from(TelemetrySample sample) {
        var document = new TelemetrySampleDocument();
        document.deviceId = sample.deviceId();
        document.edgeId = sample.edgeId();
        document.householdId = sample.householdId();
        document.sampledAt = sample.sampledAt();
        document.metrics = sample.metrics().values();
        document.expiresAt = sample.expiresAt();
        return document;
    }

    public TelemetrySample toDomain() {
        return new TelemetrySample(deviceId, edgeId, householdId, sampledAt, new EventAttributes(metrics), expiresAt);
    }
}
