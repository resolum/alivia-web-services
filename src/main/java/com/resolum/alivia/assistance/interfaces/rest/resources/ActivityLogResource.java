package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ActivityLogResource(
        Long id,
        Long assistedPersonId,
        Long careActivityId,
        Long assignmentId,
        LocalDate date,
        String title,
        LocalTime startTime,
        LocalTime endTime,
        Long assignedCaregiverId,
        String status,
        Long completedById,
        LocalDateTime completedAt,
        String notes
) {}
