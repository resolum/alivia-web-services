package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An incident with the timeline of its statuses and the time of each one, oldest first.
 */
public record IncidentResource(Long id, String householdId, Long deviceId, Long reportedBy, String description,
                               String severity, String status, String resolution, List<EvidenceResource> evidences,
                               List<IncidentStatusChangeResource> statusHistory, LocalDateTime createdAt) {
}
