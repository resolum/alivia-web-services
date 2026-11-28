package com.resolum.alivia.analytics.domain.model.aggregates;

import com.resolum.alivia.analytics.domain.model.valueobjects.MetricKind;

import java.time.Instant;
import java.util.List;

/**
 * A computed set of indicators, kept for a while so the figures shown on a given date can be found again. One per
 * kind, scope and period: computing the same period again replaces it.
 *
 * @param scope             what it is limited to, such as {@code all}, {@code plan:3} or {@code person:5}
 * @param values            the indicators as they were answered
 * @param incompleteSources the sources that could not be read when it was computed
 */
public record Metric(MetricKind kind, String scope, Instant from, Instant to, Instant computedAt, Object values,
                     List<String> incompleteSources) {

    public Metric {
        if (kind == null || scope == null || from == null || to == null || computedAt == null || values == null) {
            throw new IllegalArgumentException("A metric needs its kind, scope, period, date and values.");
        }
        incompleteSources = incompleteSources == null ? List.of() : List.copyOf(incompleteSources);
    }

    public String key() {
        return kind + "|" + scope + "|" + from + "|" + to;
    }
}
