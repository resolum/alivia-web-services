package com.resolum.alivia.tracking.infrastructure.adapters;

import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;
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

    private static final int MAX_LIMIT = 1000;

    private final MongoTemplate mongo;

    public TelemetrySampleRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void save(TelemetrySample sample) {
        mongo.insert(TelemetrySampleDocument.from(sample));
    }

    @Override
    public List<TelemetrySample> findByDevice(Long householdId, Long deviceId, Instant from, Instant to, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("The limit must be between 1 and " + MAX_LIMIT + ".");
        }
        var query = new Query(where("householdId").is(householdId).and("deviceId").is(deviceId)
                .and("sampledAt").gte(from).lt(to))
                .with(Sort.by(Sort.Direction.DESC, "sampledAt")).limit(limit);
        return mongo.find(query, TelemetrySampleDocument.class).stream().map(TelemetrySampleDocument::toDomain).toList();
    }
}
