package com.resolum.alivia.analytics.infrastructure.persistence.mongo.documents;

import com.resolum.alivia.analytics.domain.model.aggregates.Metric;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * How a {@link Metric} is kept; its id is the kind, scope and period, so computing the same period again replaces it.
 * A query without dates ends "now" and is a new period each time, so every copy expires after 90 days: the free cluster
 * holds 512 MB.
 */
@Document("analytics_metrics")
public class MetricDocument {

    @Id
    private String id;
    private String kind;
    private String scope;
    private Instant from;
    private Instant to;
    @Indexed(name = "computed_ttl", expireAfter = "90d")
    private Instant computedAt;
    /** Written as an embedded document; it is kept for the record, not read back as a Java type. */
    private Object values;
    private List<String> incompleteSources;

    protected MetricDocument() {
    }

    public static MetricDocument from(Metric metric) {
        var document = new MetricDocument();
        document.id = metric.key();
        document.kind = metric.kind().name();
        document.scope = metric.scope();
        document.from = metric.from();
        document.to = metric.to();
        document.computedAt = metric.computedAt();
        document.values = metric.values();
        document.incompleteSources = metric.incompleteSources();
        return document;
    }
}
