package com.resolum.alivia.operations.interfaces.rest.resources;

import com.resolum.alivia.operations.domain.model.valueobjects.IncidentProblemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The report form of the caregiver web: device, kind of problem, description, when it happened and the visit
 * the family chose (day, two-hour window and technician, from /appointments/available-slots).
 */
public record ReportIncidentResource(@NotBlank @Size(max = 17) String deviceMac,
                                     @NotNull IncidentProblemType problemType,
                                     @NotBlank @Size(min = 10, max = 500) String description,
                                     Instant occurredAt,
                                     @NotNull LocalDate visitDate,
                                     @NotNull LocalTime visitStartTime,
                                     @NotNull LocalTime visitEndTime,
                                     @NotNull Long technicianId) {
}
