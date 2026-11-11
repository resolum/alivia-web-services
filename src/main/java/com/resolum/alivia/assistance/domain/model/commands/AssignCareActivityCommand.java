package com.resolum.alivia.assistance.domain.model.commands;

import com.resolum.alivia.assistance.domain.model.valueobjects.ScheduleType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;

public record AssignCareActivityCommand(
        Long careActivityId,
        Long relativeId,
        Long caregiverId,
        ScheduleType scheduleType,
        Set<DayOfWeek> days,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
    public AssignCareActivityCommand {
        Objects.requireNonNull(careActivityId, "Care activity ID cannot be null.");
        Objects.requireNonNull(relativeId, "Relative ID cannot be null.");
        Objects.requireNonNull(scheduleType, "Schedule type cannot be null.");
        Objects.requireNonNull(startTime, "Start time cannot be null.");
        Objects.requireNonNull(endTime, "End time cannot be null.");
    }
}
