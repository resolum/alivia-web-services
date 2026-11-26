package com.resolum.alivia.tracking.infrastructure.adapters;

import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;
import com.resolum.alivia.tracking.domain.model.valueobjects.TelemetryPage;
import com.resolum.alivia.tracking.domain.repositories.TelemetrySampleRepository;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.TelemetrySampleDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** {@link TelemetrySampleRepository} over the {@code telemetry_samples} collection. */
@Component
public class TelemetrySampleRepositoryImpl implements TelemetrySampleRepository {

    private static final int MAX_PAGE_SIZE = 200;

    private final MongoTemplate mongo;

    public TelemetrySampleRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void save(TelemetrySample sample) {
        mongo.insert(TelemetrySampleDocument.from(sample));
    }

    @Override
    public TelemetryPage findByDevice(Long householdId, Long deviceId, Instant from, Instant to, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("The page must be 0 or more and the size between 1 and " + MAX_PAGE_SIZE + ".");
        }
        var query = new Query(where("householdId").is(householdId).and("deviceId").is(deviceId)
                .and("sampledAt").gte(from).lt(to));
        long total = mongo.count(query, TelemetrySampleDocument.class);
        if (total == 0 || (long) page * size >= total) return new TelemetryPage(List.of(), total);
        var items = mongo.find(query.with(Sort.by(Sort.Direction.DESC, "sampledAt")).skip((long) page * size).limit(size),
                TelemetrySampleDocument.class).stream().map(TelemetrySampleDocument::toDomain).toList();
        return new TelemetryPage(items, total);
    }
}
