package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TechnicalOrderResource(Long id, String draftId, String serviceType, Long incidentId, String status,
                                     String slotId,
                                     LocalDate date, String startTime, String endTime, Long technicianId,
                                     String technicianName, String contactName, String contactPhone,
                                     String directions, String evaluationResult,
                                     String installationResult, String completionNotes,
                                     MaintenanceReportResource maintenanceReport,
                                     List<AssignmentRecordResource> assignments,
                                     String code, Instant startedAt, Instant completedAt,
                                     List<InstalledDeviceResource> installedDevices,
                                     IncidentSummaryResource incident) {
}
