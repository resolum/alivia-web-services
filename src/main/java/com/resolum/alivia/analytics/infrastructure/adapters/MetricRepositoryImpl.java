package com.resolum.alivia.analytics.infrastructure.adapters;

import com.resolum.alivia.analytics.domain.model.aggregates.Metric;
import com.resolum.alivia.analytics.domain.repositories.MetricRepository;
import com.resolum.alivia.analytics.infrastructure.persistence.mongo.documents.MetricDocument;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** {@link MetricRepository} over the {@code analytics_metrics} collection of MongoDB. */
@Component
public class MetricRepositoryImpl implements MetricRepository {

    private final MongoTemplate mongo;

    public MetricRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void save(Metric metric) {
        // save is an upsert by id: the same kind, scope and period replace the earlier copy.
        mongo.save(MetricDocument.from(metric));
    }
}
