package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.time.LocalDate;

public record CancelActivityOccurrenceResource(
        Long careActivityId,
        Long assignmentId,
        LocalDate date,
        Long relativeId
) {}
