package com.resolum.alivia.analytics.domain.repositories;

import com.resolum.alivia.analytics.domain.model.aggregates.Metric;

public interface MetricRepository {

    /** Keeps the metric, replacing the one of the same kind, scope and period. */
    void save(Metric metric);
}
