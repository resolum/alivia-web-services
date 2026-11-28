package com.resolum.alivia.analytics.domain.model.queries;

import java.time.Instant;

/** @param userId the caller, who must be able to read the assisted person */
public record GetAutonomyMetricsQuery(Long userId, Long personId, Instant from, Instant to) {
}
