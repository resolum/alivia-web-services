package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.LocalDate;
import java.util.List;

public record TechnicalOrderResource(Long id, String draftId, String serviceType, String status, String slotId,
                                     LocalDate date, String startTime, String endTime, Long technicianId,
                                     String technicianName, String contactName, String contactPhone,
                                     String directions, List<AssignmentRecordResource> assignments) {
}
