package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;

public record CompleteActivityResource(
        Long careActivityId,
        Long assignmentId,
        LocalDate date,
        Long caregiverUserId,
        String notes
) {}
