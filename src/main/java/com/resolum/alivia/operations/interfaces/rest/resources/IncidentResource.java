package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An incident with what the family needs to follow it: the timeline of its statuses with their time and the
 * maintenance visits booked for it, oldest first.
 */
public record IncidentResource(Long id, String householdId, Long deviceId, Long reportedBy, String description,
                               String severity, String status, String resolution, List<EvidenceResource> evidences,
                               List<IncidentStatusChangeResource> statusHistory,
                               List<MaintenanceVisitResource> visits, LocalDateTime createdAt) {
}
