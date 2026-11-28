package com.resolum.alivia.analytics.domain.model.queries;

import java.time.Instant;

/** {@code from} and {@code to} may be {@code null}: see {@code AnalyticsPeriod.of}. */
public record GetSalesMetricsQuery(Instant from, Instant to, Long planId) {
}
