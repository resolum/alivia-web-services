package com.resolum.alivia.support.interfaces.rest.resources;

import java.time.LocalDate;

public record AppointmentResource(Long id, String draftId, LocalDate date, String startTime, String endTime,
                                  Long technicianId, String technicianName, String contactName,
                                  String contactPhone, String directions, String status) {
}
