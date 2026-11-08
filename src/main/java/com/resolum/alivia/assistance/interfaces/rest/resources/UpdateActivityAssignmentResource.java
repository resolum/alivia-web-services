package com.resolum.alivia.assistance.interfaces.rest.resources;

import com.resolum.alivia.assistance.domain.model.valueobjects.ScheduleType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record UpdateActivityAssignmentResource(
        Long relativeId,
        Long caregiverId,
        ScheduleType scheduleType,
        Set<DayOfWeek> days,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {}
