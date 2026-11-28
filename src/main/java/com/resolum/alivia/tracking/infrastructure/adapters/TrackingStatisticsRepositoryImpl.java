package com.resolum.alivia.tracking.infrastructure.adapters;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventSeverity;
import com.resolum.alivia.tracking.domain.repositories.TrackingStatisticsRepository;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceEventDocument;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceStateDocument;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** {@link TrackingStatisticsRepository} over the {@code device_events} and {@code device_states} collections. */
@Component
public class TrackingStatisticsRepositoryImpl implements TrackingStatisticsRepository {

    private static final String INFORMATIVE = EventSeverity.INFORMATIVE.name();

    private final MongoTemplate mongo;

    public TrackingStatisticsRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Map<String, Long> countAlertsBySeverity(Instant from, Instant to) {
        var alerts = where("occurredAt").gte(from).lt(to).and("severity").ne(INFORMATIVE);
        return countBy(DeviceEventDocument.class, alerts, "severity");
    }

    @Override
    public Map<String, Long> countDevicesByConnectionStatus() {
        return countBy(DeviceStateDocument.class, new Criteria(), "connectionStatus");
    }

    @Override
    public List<DeviceEvent> findAlertsByHousehold(Long householdId, Instant from, Instant to, int limit) {
        // Served by the household_time_id index.
        var query = new Query(where("householdId").is(householdId).and("occurredAt").gte(from).lt(to)
                .and("severity").ne(INFORMATIVE))
                .with(Sort.by(Sort.Direction.ASC, "occurredAt", "id")).limit(limit);
        return mongo.find(query, DeviceEventDocument.class).stream().map(DeviceEventDocument::toDomain).toList();
    }

    private Map<String, Long> countBy(Class<?> document, Criteria criteria, String field) {
        var aggregation = Aggregation.newAggregation(Aggregation.match(criteria), Aggregation.group(field).count().as("n"));
        var counts = new LinkedHashMap<String, Long>();
        for (var row : mongo.aggregate(aggregation, document, Document.class).getMappedResults()) {
            var key = row.get("_id");
            if (key != null) counts.put(key.toString(), ((Number) row.get("n")).longValue());
        }
        return counts;
    }
}
