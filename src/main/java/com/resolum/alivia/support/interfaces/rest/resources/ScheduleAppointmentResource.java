package com.resolum.alivia.support.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleAppointmentResource(
        @NotBlank String draftId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull Long technicianId,
        @NotBlank @Size(max = 100) String contactName,
        @NotBlank @Size(max = 20) String contactPhone,
        @Size(max = 300) String directions) {
}
