package com.resolum.alivia.hcm.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkShiftResource(@NotNull DayOfWeek day, @NotNull LocalTime startTime,
                                @NotNull LocalTime endTime) {
}
