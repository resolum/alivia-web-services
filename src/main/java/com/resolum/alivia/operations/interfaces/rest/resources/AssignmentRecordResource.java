package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.Instant;

public record AssignmentRecordResource(Long technicianId, String technicianName, String slotId, String reason,
                                       String note, Instant assignedAt) {
}
