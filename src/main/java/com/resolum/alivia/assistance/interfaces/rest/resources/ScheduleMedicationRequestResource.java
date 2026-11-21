package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.util.List;

public record ScheduleMedicationRequestResource(
        String medication,
        String dose,
        List<String> times,
        String frequency,
        String startDate,
        String endDate,
        Long caregiverId,
        Long assistedPersonId,
        String indicationRef
) {}
