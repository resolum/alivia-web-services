package com.resolum.alivia.analytics.domain.model.queries;

import java.time.Instant;

public record GetBusinessOverviewQuery(Instant from, Instant to) {
}
