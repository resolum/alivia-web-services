package com.resolum.alivia.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record HoldEvaluationSlotResource(
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull Long technicianId) {
}
