package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.LocalDateTime;
import java.util.List;

public record IncidentResource(Long id, String householdId, Long deviceId, Long reportedBy, String description,
                               String severity, String status, String resolution, List<EvidenceResource> evidences,
                               LocalDateTime createdAt) {
}
