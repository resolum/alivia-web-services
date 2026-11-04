package com.resolum.alivia.operations.domain.model.queries;

import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;

/** Incidents reported by {@code reportedBy} (null: every account), optionally of one status. */
public record GetIncidentsQuery(Long reportedBy, IncidentStatus status) {
}
