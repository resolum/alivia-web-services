package com.resolum.alivia.tracking.interfaces.rest.resources;

import java.util.List;

/** The outcome of every event of the batch, in the same order. */
public record EventBatchOutcomeResource(List<EventOutcomeResource> results) {
}
