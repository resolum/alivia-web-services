package com.resolum.alivia.subscriptions.domain.model.queries;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringStage;

/** Hiring requests of one stage, or of every stage when {@code stage} is null. */
public record GetHiringRequestsQuery(HiringStage stage) {
}
