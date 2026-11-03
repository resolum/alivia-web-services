package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.LocalDate;
import java.time.LocalTime;

public record MaintenanceVisitResource(Long orderId, LocalDate scheduledDate, LocalTime startTime, LocalTime endTime,
                                       Long technicianId, String technicianName, String status) {
}
